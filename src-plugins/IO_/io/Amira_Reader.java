package io;

// Amira_Reader.java
// 
// Plugin to import Amira mesh images
// v0.1 by Greg 2005-11-16
// TODO
// Add support for multichannel data
// Test with various file types
// Verify Cross Platform read capability
// Finish support for gzipped files
// ----------
// v 0.1.1 2005-12-17
// - Removed requirement that lattice definition be of form
//   Lattice { byte Data } - Data can be anything now.
// ----------
// v 0.1.2 2007-04-

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class Amira_Reader extends ImagePlus implements PlugIn {

	private BufferedInputStream f;
	private String notes;
	private String mode;
	
	// In interactive mode errors results in a dialog box
	private boolean interactiveMode=true;

	public void run(String arg) {
		// if an argument is supplied, consider that
		// we are in batch mode and suppress messages
		if (!arg.equals("")) {
			interactiveMode=false;
		}
		
		OpenDialog od = new OpenDialog("Open Amira Mesh...", arg);
		String directory = od.getDirectory();
		String fileName = od.getFileName();
		if (fileName==null)
			return;
		IJ.showStatus("Opening: " + directory + fileName);

		FileInfo fi = null;FileOpener fo=null;
		try {
			fi = getHeaderInfo(directory, fileName);
			fo = openAmiraData(fi);
		}
		catch (Exception e) {
			IJ.showStatus("");
			if(interactiveMode) {
				IJ.showMessage("AmiraReader", ""+e);
			} else {
				IJ.log("AmiraReader: "+e);
			}
			return;
		}
		if (IJ.debugMode) IJ.log("FileInfo: "+fi);

		if(fo != null){
			ImagePlus imp = fo.open(false);
			if (imp==null)
				return;
			if (imp.getStackSize()>1)
				setStack(fileName, imp.getStack());
			else
				setProcessor(fileName, imp.getProcessor());

			// set the fileinfo field of the current ImagePlus
			// this is useful because the origical filename
			// and path can now be retrieved from getOriginalFileInfo()
			setFileInfo(fi);
			
			if (!notes.equals(""))
				setProperty("Info", notes);
			//copyScale(imp);
			if (arg.equals("")) show();
		}
	}
	FlexibleFileOpener openAmiraData (FileInfo fi) throws IOException {
		if (IJ.debugMode) IJ.log("running openAmiraData");
		//		if (mode==null) return new FlexibleFileOpener(fi);
		if (mode==null) return new FlexibleFileOpener(fi);
		if (mode.equals("Zip")){
			int preOffset=fi.offset;
			fi.offset=0;
			return new FlexibleFileOpener(fi,FlexibleFileOpener.ZLIB,preOffset);
		}
		// Error handling?
		throw new IOException("Unknown data type: "+mode);
	}
	int getByte() throws IOException {
		int b = f.read();
		if (b ==-1) throw new IOException("unexpected EOF");
		return b;
	}

	int getShort() throws IOException {
		int b0 = getByte();
		int b1 = getByte();
		return ((b1 << 8) + b0);
	}
	int getInt() throws IOException {
		int b0 = getShort();
		int b1 = getShort();
		return ((b1<<16) + b0);
	}

	FileInfo getHeaderInfo(String directory, String fileName) throws IOException {
		FileInfo fi = new FileInfo();
		fi.fileFormat = FileInfo.RAW;
		fi.fileName = fileName;
		fi.directory = directory;
		// To find offset, just keep reading until we get a line starting
		// with @1
		AmiraHeader ah=new AmiraHeader(directory + fileName);		
		notes = ah.toString();
		// Assume that the data comes straight after the header line read
		// by 
		fi.offset=(int) ah.offset;
		/*
		# AmiraMesh 3D BINARY 2.0
		# CreationDate: Wed Nov 16 10:25:02 2005
		define Lattice 375 375 30
		Parameters {
			Content "375x375x30 byte, uniform coordinates",
			BoundingBox 0 168.78 0 168.78 0 72.5,
			CoordType "uniform"
		}
		Lattice { byte Data } @1
		# Data section follows
		@1
		*/
		
		Matcher amiraMeshDef = Pattern.compile("#\\s+AmiraMesh.*?(BINARY|ASCII)(-LITTLE-ENDIAN)*").matcher(notes);
		if(amiraMeshDef.find()) {
			if(amiraMeshDef.group(1).equals("BINARY")) {
				// fine
				if(amiraMeshDef.group(2).equals("-LITTLE-ENDIAN")) fi.intelByteOrder=true;
				else fi.intelByteOrder=false;
			} else if(amiraMeshDef.group(1).equals("ASCII")) {
				throw new IOException("Unable to read Amira ASCII files");
			} else throw new IOException("Can't recognise this Amira file");
		} else throw new IOException("Doesn't seem to be an Amira file");
		

		Matcher latticeDef = Pattern.compile("define\\s+Lattice\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)").matcher(notes);
		if(latticeDef.find()) {
		   fi.width = s2i(latticeDef.group(1)); // Captured by parentheses
		   fi.height = s2i(latticeDef.group(2)); // Captured by parentheses
		   fi.nImages = s2i(latticeDef.group(3)); // Captured by parentheses
		}
		/* 
		 * #     Amira docs: The primitive data types must be
		 * #        one of byte, short, int, float, double, or complex.  Vectors of
		 * #        primitive data types are allowed, aggregate structs are not, however.
		 *         
		 * #        primType Returns the primitive data type of the field, i.e., the way
		 * #        how the values are represented internally.  A number with the following
		 * #        meaning is returned: 0 = bytes, 1 = 16-bit signed integers, 2 = 32-bit
		 * #        signed integers, 3 = 32-bit floating point values, 4 = 64-bit floating
		 * #        point values, 7 = 16-bit unsigned integers.
		 */

		// GJ removed requirement that Lattice definition statement contain a specific
		// name eg Data or ScalarField
		// Lattice { byte Data } @1
		Matcher dataTypeDef = Pattern.compile("Lattice\\s+\\{\\s+(\\w+)\\s+\\w+\\s+\\}.*@(\\d+)").matcher(notes);
		
		if(dataTypeDef.find()) {
		   if (dataTypeDef.group(1).toLowerCase().equals("byte") ) {
			   fi.fileType = FileInfo.GRAY8;
		   } else if (dataTypeDef.group(1).toLowerCase().equals("short") ) {
			   // Don't know if shorts/ints should be signed
			   fi.fileType = FileInfo.GRAY16_SIGNED;
		   } else if (dataTypeDef.group(1).toLowerCase().equals("ushort") ) {
			   fi.fileType = FileInfo.GRAY16_UNSIGNED;
		   } else if (dataTypeDef.group(1).toLowerCase().equals("int") ) {
			   // Don't know if shorts/ints should be signed
			   fi.fileType = FileInfo.GRAY32_INT;  
		   } else if (dataTypeDef.group(1).toLowerCase().equals("float") ) {
			   fi.fileType = FileInfo.GRAY32_FLOAT;
		   }
		} else {
			IJ.log("Assuming data type is byte");
			fi.fileType = FileInfo.GRAY8;
		}
		
//		Matcher modeMatcher=Pattern.compile("Hx(ByteRLE),(\\d+).*").matcher(notes);
		Matcher modeMatcher=Pattern.compile("Lattice.*Hx(Zip|ByteRLE),(\\d+)").matcher(notes);
		if(modeMatcher.find()) {
			mode = modeMatcher.group(1);
			if (IJ.debugMode) IJ.log("Mode = "+mode);
			//zLength = Integer.parseInt(modeMatcher.group(2));
		} else {
			if (IJ.debugMode) IJ.log("Mode = "+mode);
		}		
		
		String n = "(-?\\d+\\.?\\d*)";
		String sixNumbersPat=n+"\\s+"+n+"\\s+"+n+"\\s+"+n+"\\s+"+n+"\\s+"+n;
		Matcher boundingBoxDef = Pattern.compile("BoundingBox "+sixNumbersPat).matcher(notes);
		if(boundingBoxDef.find()) {
			Calibration mycal=getCalibration();
			double xwidth=s2d(boundingBoxDef.group(2))-s2d(boundingBoxDef.group(1));
			double yheight=s2d(boundingBoxDef.group(4))-s2d(boundingBoxDef.group(3));
			double zdepth=s2d(boundingBoxDef.group(6))-s2d(boundingBoxDef.group(5));
			
			// Note that amira defines the bounding box as the box
			// surrounding the centres of the extreme voxels
			mycal.pixelWidth=xwidth/(fi.width-1);
			mycal.pixelHeight=yheight/(fi.height-1);
			// ... which means that height is undefined for a 1 slice stack
			if(fi.nImages>1) mycal.pixelDepth=zdepth/(fi.nImages-1);
			// Now apply this calibration to this ImagePlus derived object
			setCalibration(mycal);
			
			if (IJ.debugMode) IJ.log("Cal: "+mycal);
		}

		if (IJ.debugMode) IJ.log("FileInfo: "+fi);
  		return fi;
	}
		
	// Converts a string to a double. Returns 1.0 if the string does not contain a valid number. */
	double s2d(String s) {
		Double d = null;
		try {d = new Double(s);}
		catch (NumberFormatException e) {}
		return d!=null?d.doubleValue():1.0;
	}
	int s2i(String s) {
		Integer d = null;
		try {d = new Integer(s);}
		catch (NumberFormatException e) {}
		return d!=null?d.intValue():1;
	}
	/* Copied over from AmiraMeshReader_
		Will try using this as inspiration
	public ColorModel getColorModel() {
		if(materials==null || materials.size()==0)
			return null;
		byte[] r=new byte[256];
		byte[] g=new byte[256];
		byte[] b=new byte[256];
		
		for(int i=0;i<getMaterialCount();i++) {
			double[] color=getMaterialColor(i);
			r[i]=(byte)Math.round(color[0]*255);
			g[i]=(byte)Math.round(color[1]*255);
			b[i]=(byte)Math.round(color[2]*255);
		}
		return new IndexColorModel(8,256,r,g,b);
	}
	*/
	//  Based on Bruce Eckel's TextFile class
	class AmiraHeader extends ArrayList {
		// could be used when serialising
		private static final long serialVersionUID = 1L;
		// Tools to read and write files as single strings:
		String stringval;
		protected long offset=0;
		
		public AmiraHeader(String fileName) throws IOException {
			StringBuffer sb = new StringBuffer(); String s;
			if(fileName.endsWith(".gz")) {
				// Can't find a way to keep track of how many characters
				// have been read INCLUDING line terminators				
				
				throw new IOException("Unable to read gzipped Amira files");
				// The gzinput code below is no good because it doesn't get
				// the number of bytes read correct because of buffering

				/* 
				 * GJGZIPInputStream zstream = new GJGZIPInputStream(new FileInputStream(fileName));
				 * BufferedReader in = new BufferedReader(new InputStreamReader(zstream));
				 *  // Keep going until we run out of text to read or we get to the 
				 *  // first data section
				 *  while((s = in.readLine()) != null && !s.trim().equals("@1") ){
				 *      sb.append(s+"\n");
				 *  }
				 *  offset=zstream.bytesRead();
				 *  if (IJ.debugMode) IJ.log("offset: "+offset);
				 *  in.close();                 
				 */
			} else {	
				// NB Need RAF in order to ensure that we know file offset
				RandomAccessFile in = new RandomAccessFile(fileName,"r");
//				in = new BufferedReader(new FileReader(fileName));
				// Keep going until we run out of text to read or we get to the 
				// first data section
				while((s = in.readLine()) != null && !s.trim().equals("@1") ){
					sb.append(s+"\n");
				}
				offset=in.getFilePointer();
				in.close();
			}
			stringval=sb.toString();
			if (IJ.debugMode) IJ.log("header string length: "+stringval.length());
			if (IJ.debugMode) IJ.log("offset: "+offset);
		}
		public String toString() { return (stringval); }
		
		class GJGZIPInputStream  extends GZIPInputStream {
			// Class that will count the number of bytes that have been read
			// from a GZIPInputStream
			// Hmm actually fails to work because of buffering
			int bytesRead=0;
			
			public GJGZIPInputStream(InputStream in) throws IOException {
				super(in);
			}

			public GJGZIPInputStream(InputStream in, int size) throws IOException {
				super(in,size);
			}

			/* 
			 * public int read() throws IOException {
			 *     int rval=super.read();
			 *     if(rval!=-1) bytesRead++;
			 *     return rval;
			 * }
			 */
			public int read(byte[] b, int off, int len) throws IOException {
				int rval=super.read(b, off, len);
				if(rval!=-1) bytesRead+=rval;
				if (IJ.debugMode) IJ.log("bytesRead: "+bytesRead+"; rval: "+rval);
				return rval;
			}
			
			public int bytesRead() { return bytesRead; }						
		}
		
	}	
}
