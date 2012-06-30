/*
  Copyright 2005, 2006 by Lars Knipping

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package org.siox.util;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A hashmap that uses primitive ints for the key rather than objects
 * to avoid costly usage of <CODE>Integer</CODE> wrappers.
 *
 * @author Lars Knipping

 * @version 1.01
 * @see java.util.HashMap
 */
public class IntHashMap<V> {
	
  // CHANGELOG
  // 2005-11-21 1.01 fixed rehash size bug
  // 2005-11-18 1.00 initial release.
  //                 CAVEAT: Most methods are completely untested for now.

  /** Constant for constructing a key iterator. */
  private static final int KEYS = 0;
  /** Constant for constructing a value iterator. */
  private static final int VALUES = 1;
  /** Constant for constructing an entry iterator. */
  private static final int ENTRIES = 2;

  /** The hashmap data. */
  private Entry<V>[] data;
  /** The total number of entries in the map. */
  private int size;
  /**
   * Map is rehashed when its size exceeds this.
   * <P>
   * Value of this field is capacity times loadFactor.
   */
  private int threshold;
  /** Load factor for the map, determining when to increase table size. */
  private float loadFactor;
  /** The number of times this list has been structurally modified. */
  protected transient int modCount = 0;

  /**
   * Constructs an empty HashMap with the default initial capacity (16)
   * and the default load factor (0.75).
   */
  public IntHashMap() {
	this(16, 0.75F);
  }

  /**
   * Constructs an empty HashMap with the specified initial capacity and
   * the default load factor (0.75).
   */
  public IntHashMap(int initialCapacity) {
	this(initialCapacity, 0.75F);
  }

  /**
   * Constructs an empty HashMap with the specified initial capacity and
   * load factor.
   */
  public IntHashMap(int initialCapacity, float loadFactor){
	data = new Entry[initialCapacity];
	this.loadFactor = loadFactor;
  }

  /**
   * Constructs a new HashMap with the same mappings as the specified map.
   */
  public IntHashMap(Map<Integer, Object> aMap) {
	this(Math.max(2*aMap.size(), 16), 0.75f); // some space for growing
	putAll(aMap);
  }

  /** Constructs a new HashMap with the same mappings as the specified Map. */
  public IntHashMap(IntHashMap<V> anIntHashMap) {
	this(Math.max(2*anIntHashMap.size(), 16), 0.75f);//some space for growing
	putAll(anIntHashMap);
  }


  /** Removes all mappings from this map. */
  public void clear(){
	if (size > 0) {
	  ++modCount;
	  Arrays.fill(data, 0, size-1, null);
	  size = 0;
	}
  }

  /**
   * Returns a shallow copy of this HashMap instance: the values themselves
   * are not cloned.
   */
  public Object clone() {
	final IntHashMap<V> anIntHashMap = new IntHashMap<V>(data.length, loadFactor);
	System.arraycopy(data, 0, anIntHashMap.data, 0, size);
	anIntHashMap.size = size;
	return anIntHashMap;
  }

  private int hash(int key, int tablelength) {
	// basic implementation just uses the unmodified int key,
	// method introduced to easily change hashing strategy
	return (key&0x7FFFFFFF) % tablelength;
  }

  /** Returns true if this map contains a mapping for the specified key. */
  public boolean containsKey(int key) {
	final Entry<V>[] table = data;
	final int idx = hash(key, table.length);
	for (Entry<V> anEntry=table[idx]; anEntry!=null; anEntry=anEntry.next)
	  if (anEntry.key==key)
		return true;
	return false;
  }

  /**
   * Returns true if this map contains a mapping for the intvalue of the
   * specified Integer as key.
   */
  public boolean containsKey(Integer key){
	return containsKey(key.intValue());
  }

  /** Returns true if this map maps one or more keys to the specified value. */
  public boolean containsValue(Object value) {
	final Entry<V>[] table = data;
	if (value == null) {
	  for (int i=0; i<data.length; ++i)
		for (Entry<V> anEntry=table[i]; anEntry!=null; anEntry=anEntry.next)
		  if (null == anEntry.value)
			return true;
	} else {
	  for (int i=0; i<data.length; ++i)
		for (Entry<V> anEntry=table[i]; anEntry!=null; anEntry=anEntry.next)
		  if (value.equals(anEntry.value))
			return true;
	}
	return false;
  }

  /**  Returns a collection view of the mappings contained in this map. */
  public Set<Entry<V>> entrySet(){
	return new AbstractSet<Entry<V>>() {
		public void clear() {
		  IntHashMap.this.clear();
		}
		public boolean contains(final Object o) {
		  if (!(o instanceof Map.Entry))
			return false;
		  final Entry<V> searched = (Entry<V>) o;
		  final Entry<V>[] table = IntHashMap.this.data;
		  final int idx = IntHashMap.this.hash(searched.key, table.length);
		  for (Entry<V> entry=table[idx]; entry!=null; entry=entry.next)
			if (entry.equals(searched))
			  return true;
		  return false;
		}
		public Iterator<Entry<V>> iterator() {
		  return new MapIterator<Entry<V>>(ENTRIES);
		}
		public boolean remove(final Object o) {
		  if (!(o instanceof Entry))
			return false;
		  return IntHashMap.this.removeEntry((Entry<V>) o);
		}
		public int size() {
		  return IntHashMap.this.size;
		}
	  };
  }

  /** Compares the specified object with this map for equality. */
  public boolean equals(Object o){
	if (o == this)
	  return true;
	if (!(o instanceof IntHashMap))
	  return false;
	final IntHashMap<V> anIntHashMap = (IntHashMap<V>) o;
	if (anIntHashMap.size != size)
	  return false;
	final Iterator<Entry<V>> anIterator = entrySet().iterator();
	while ( anIterator.hasNext()) {
	  final Entry<V> anEntry = anIterator.next();
	  if (anEntry.value == null) {
		if (!(anIntHashMap.containsKey(anEntry.key)
			  && anIntHashMap.get(anEntry.key)==null))
		  return false;
	  } else {
		if (!anEntry.value.equals(anIntHashMap.get(anEntry.key)))
		  return false;
	  }
	}
	return true;
  }

  /**
   * Returns the value to which the specified key is mapped in this
   * hashmap, or null if the map contains no mapping for this key.
   */
  public V get(int key){
	final Entry<V>[] table = data;
	final int idx =  hash(key, table.length);
	for (Entry<V> anEntry=table[idx]; anEntry!=null; anEntry=anEntry.next)
	  if (anEntry.key==key)
		return anEntry.value;
	return null;
  }

  /**
   * Returns the value to which the specified Integers int value is mapped
   * in this hashmap, or null if the map contains no mapping for the key.
   */
  public Object get(Integer key) {
	return get(key.intValue());
  }

  /** Returns the hash code value for this map. */
  public int hashCode(){
	int hash = 0;
	final Iterator<Entry<V>> anIterator = entrySet().iterator();
	while (anIterator.hasNext())
	  hash += anIterator.next().hashCode();
	return hash;
  }

  /**  Returns true if this map contains no key-value mappings. */
  public boolean isEmpty(){
	return size == 0;
  }

  /**
   * Returns a set view of the keys contained in this map, with the int
   * keys wrapped into <CODE>Integer</CODE> objects.
   */
  public Set<Integer> keySet() {
	return new AbstractSet<Integer>() {
		public Iterator<Integer> iterator() {
		  return new MapIterator<Integer>(KEYS);
		}
		public int size() {
		  return IntHashMap.this.size;
		}
		public boolean contains(Object o) {
		  return IntHashMap.this.containsKey((Integer) o);
		}
		public boolean remove(Object o) {
		  if (!(o instanceof Integer))
			return false;
		  final Entry<V> entry =
			IntHashMap.this.removeEntryByKey(((Integer) o).intValue());
		  if (entry != null)
			entry.value = null; // help gc
		  return entry != null;
		}
		public void clear() {
		  IntHashMap.this.clear();
		}
	  };
  }

  /**
   * Associates the specified value with the specified key in this map.
   *
   * @return the previous value of the specified key in this map
   *         or <CODE>null</CODE> if it did not have one.
   */
  public V put(int key, V value) {
	// already present in map?
	Entry<V>[] table = data;
	int idx = hash(key, table.length);
	for (Entry<V> anEntry=table[idx]; anEntry!=null; anEntry=anEntry.next)
	  if (anEntry.key==key) {
		final V old = anEntry.value;
		anEntry.value = value;
		return old;
	  }
	++modCount;
	// check for resize
	if (size >= threshold) {
	  // increase table size and rehash:
	  final Entry<V>[] oldData = data;
	  final Entry<V>[] newData = new Entry[2*oldData.length+1];
	  ++modCount;
	  threshold = (int) (newData.length * loadFactor);
	  for (int i=0; i<oldData.length; ++i) {
		Entry<V> entry=oldData[i];
		while (entry!=null) {
		  final Entry<V> curr = entry;
		  entry = entry.next;
		  final int newIdx = hash(curr.key, newData.length);
		  curr.next = newData[newIdx];
		  newData[newIdx] = curr;
		}
	  }
	  data = newData;
	  // update local vars
	  table = data;
	  idx = hash(key, table.length);
	}
	// add new entry
	table[idx] = new Entry<V>(key, value, table[idx]);
	++size;
	return null;
  }

  /**
   * Associates the specified value with the int valie of the specified
   * Integer as key in this map.
   */
  public Object put(Integer key, Object value) {
	return put(key.intValue(), value);
  }

  private<T extends Map.Entry<Integer, Object>> void putAll(Iterator<T> aMapEntryInterator) {
	while (aMapEntryInterator.hasNext()) {
	  T anEntry = aMapEntryInterator.next();
	  put(anEntry.getKey(), anEntry.getValue());
	}
  }

  /**
   * Copies all of the mappings from the specified map to this map.
   * These mappings will replace any mappings that this map had for any
   * of the keys currently in the specified map.
   */
  public void putAll(IntHashMap anIntHashMap) {
	putAll(anIntHashMap.entrySet().iterator());
  }

  /**
   * Copies all of the mappings from the specified map to this map.
   * These mappings will replace any mappings that this map had for any
   * of the keys currently in the specified map.
   *
   * @exception ClassCastException if a key of class other than Integer is
   *            contained in the given map.
   */
  public void putAll(Map<Integer, Object> aMap){
	putAll(aMap.entrySet().iterator());
  }

  // for set returned by entrySet()
  private boolean removeEntry(Entry<V> anEntry) {
	final Entry<V>[] table = data;
	final int idx = hash(anEntry.key, table.length);
	for (Entry<V> curr=table[idx],prev=null; curr!=null; prev=curr,curr=curr.next)
	  if (anEntry.equals(curr)) {
		++modCount;
		if (prev == null)
		  table[idx] = curr.next;
		else
		  prev.next = curr.next;
		return true;
	  }
	return false;
  }

  public Entry<V> removeEntryByKey(int key){
	final Entry<V>[] table = data;
	final int idx = hash(key, table.length);
	for (Entry<V> curr=table[idx],prev=null; curr!=null; prev=curr,curr=curr.next)
	  if (curr.key==key) {
		++modCount;
		if (prev == null)
		  table[idx] = curr.next;
		else
		  prev.next = curr.next;
		return curr;
	  }
	return null;
  }

  /**
   * Removes the mapping for this key from this map if present.
   *
   * @return the value to which the key had been mapped in this map,
   *         or <CODE>null</CODE> if the key did not have a mapping.
   */
  public Object remove(int key){
	final Entry<V> removed = removeEntryByKey(key);
	if (removed != null) {
	  final Object value = removed.value;
	  removed.value = null; // help gc
	  return value;
	}
	return null;
  }

  /**
   * Removes the mapping for the int value of the given Integer as key
   * from this map if present.
   */
  public Object remove(Integer key){
	return remove(key.intValue());
  }

  /** Returns the number of key-value mappings in this map. */
  public int size() {
	return size;
  }

  /** Returns a string representation of this map. */
  public String toString(){
	final Iterator<Entry<V>> anIterator = entrySet().iterator();
	final StringBuffer aStringBuffer = new StringBuffer();
	aStringBuffer.append("IntHashMap{");
	if (anIterator.hasNext()) {
	  final Entry<V> anEntry = anIterator.next();
	  aStringBuffer.append(anEntry.key).append("=").append(anEntry.value);
	}
	while (anIterator.hasNext()) {
	  aStringBuffer.append(",");
	  final Entry<V> anEntry = anIterator.next();
	  aStringBuffer.append(anEntry.key+"="+anEntry.value);
	}
	aStringBuffer.append("}");
	return aStringBuffer.toString();
  }

  /** Returns a collection view of the values contained in this map. */
  public Collection<V> values() {
	return new AbstractCollection<V>() {
		public Iterator<V> iterator() {
		  return new MapIterator<V>(VALUES);
		}
		public int size() {
		  return size;
		}
		public boolean contains(Object o) {
		  return containsValue(o);
		}
		public void clear() {
		  IntHashMap.this.clear();
		}
	  };
  }

  ///////////////////////////////////////////////////////////////////////////
  // INNER CLASSES
  ///////////////////////////////////////////////////////////////////////////

  /** Class for representing a key-value-pair in the map. */
  private static class Entry<V> implements Map.Entry<Integer, V>, Serializable {

	/**
	 * Generated serial version UID
	 */
	private static final long serialVersionUID = 577580285278842632L;
	
	final int key;
	V value;
	Entry<V> next;

	Entry(int key, V value, Entry<V> next) {
	  this.key = key;
	  this.value = value;
	  this.next = next;
	}

	/** Compares the specified object with this entry for equality. */
	public boolean equals(Object o) {
	  if (!(o instanceof Entry))
		return false;
	  final Entry<V> anEntry = (Entry<V>) o;
	  return (key==anEntry.key) &&
		((value==null)?(anEntry.value==null):(value.equals(anEntry.value)));
	}

	/** Returns the key corresponding to this entry, wrapped to an Integer. */
	public Integer getKey() {
	  return key;
	}

	/** Returns the value corresponding to this entry. */
	public V getValue() {
	  return value;
	}

	/** Returns the hash code value for this map entry. */
	public int hashCode() {
	  return key ^ (value==null ? 0 : value.hashCode());
	}

	/** Replaces the value in this entry with the specified value. */
	public V setValue(V value) {
	  final V oldValue = this.value;
	  this.value = value;
	  return oldValue;
	}
  }

  /** Class for providing the iterators on the map. */
  private class MapIterator<E> implements Iterator<E> {

	private final Entry<V>[] table = IntHashMap.this.data;
	private int index = -1;
	private Entry<V> currEntry = null;
	private Entry<V> prevEntry = null;
	private int validModCount = IntHashMap.this.modCount;
	// Iterator for keys, values, or entries?
	private int type;

	// type must be one of the constants KEYZ, VALUES, ENTRIES
	MapIterator(int type) {
	  this.type = type;
	}

	public boolean hasNext() {
	  Entry<V> entry = currEntry;
	  int i = index;
	  while (entry==null && i<table.length)
		entry = table[++i];
	  index = i;
	  return (currEntry=table[index]) != null;
	}

	public E next() {
	  if (modCount != validModCount)
		throw new ConcurrentModificationException();
	  Entry<V> entry = currEntry;
	  int i = index;
	  while (entry==null && i<table.length)
		entry = table[++i];
	  index = i;
	  if (entry == null)
		throw new NoSuchElementException();
	  prevEntry = currEntry;
	  currEntry = entry.next;
	  if (type == ENTRIES)
		return (E) entry;
	  else
		return (E) ((type==KEYS) ? entry.key : entry.value);
	}

	public void remove() {
	  if (prevEntry == null)
		throw new IllegalStateException();
	  if (modCount != validModCount)
		throw new ConcurrentModificationException();
	  final Object o = IntHashMap.this.remove(prevEntry.key);
	  ++validModCount;
	  if (o==null && modCount!=validModCount)
		throw new ConcurrentModificationException();
	}
  }
}

