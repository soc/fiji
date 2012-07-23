package fiji.plugin;

import java.util.NoSuchElementException;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;

public enum TransformationModel {
	TRANSLATION("Translation") {
		@Override
		public AbstractAffineModel3D getModel() {
			return new TranslationModel3D();
		}
	}, RIGID("Rigid") {
		@Override
		public AbstractAffineModel3D getModel() {	
			return new RigidModel3D();
		}
	}, AFFINE("Affine") {
		@Override
		public AbstractAffineModel3D getModel() {
			return new AffineModel3D();
		}
	};
	
	public abstract AbstractAffineModel3D getModel();
	
	private TransformationModel(String description) {
		this.description = description;
	}
	
	public final String description;
	
	static final String[] descriptions;
	
	static {
		BeadRegistrationType[] brts = BeadRegistrationType.values();
		descriptions = new String[brts.length];
		for (int i = 0; i < brts.length; i++) {
			descriptions[i] = brts[i].description;
		}
	}
	
	public static String[] getDescriptions() {
		return descriptions.clone();
	}
		
	private static TransformationModel ordinalOf(int ord) {
		return values()[ord];
	}
	
	public static TransformationModel byDescription(String description){
		return ordinalOf(indexOf(descriptions, description));	
	}
	
	private static <T> int indexOf(T[] haystack, T needle) {
		for(int i = 0; i < haystack.length; i ++) {
			if(haystack[i].equals(needle))
				return i;
		}
		throw new NoSuchElementException();
	}
}
