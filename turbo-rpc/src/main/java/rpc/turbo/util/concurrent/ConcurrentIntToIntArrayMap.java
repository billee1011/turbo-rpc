package rpc.turbo.util.concurrent;

import static rpc.turbo.util.UnsafeUtils.unsafe;

import java.util.Arrays;
import java.util.function.IntSupplier;

/**
 * 高性能，仅适用于key值比较少并且key、value大于0的情况，最多不能超过512k个
 * 
 * @author Hank
 *
 */
public class ConcurrentIntToIntArrayMap {
	private static final int NOT_FOUND = -1;
	private static final int MAXIMUM_CAPACITY = 1024 * 512;

	private static final int ABASE;
	private static final int ASHIFT;

	private volatile int[] array;

	public ConcurrentIntToIntArrayMap() {
		this(16);
	}

	public ConcurrentIntToIntArrayMap(int initialCapacity) {
		if (initialCapacity < 2) {
			throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
		}

		if (initialCapacity > MAXIMUM_CAPACITY) {
			throw new IndexOutOfBoundsException("Illegal initial capacity: " + initialCapacity);
		}

		ensureCapacity(initialCapacity);
	}

	/**
	 * 
	 * @param key
	 * @return
	 */
	public boolean contains(int key) {
		if (key < 0) {
			return false;
		}

		int[] finalArray = array;
		if (key >= finalArray.length) {
			return false;
		}

		int value = unsafe().getIntVolatile(finalArray, offset(key));

		if (value == NOT_FOUND) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 
	 * @param key
	 * @return -1 为找不到
	 */
	public int get(final int key) {
		if (key < 0) {
			throw new IllegalArgumentException("Illegal key: " + key);
		}

		final int[] finalArray = array;
		if (key >= finalArray.length) {
			return NOT_FOUND;
		}

		int value = unsafe().getIntVolatile(finalArray, offset(key));
		return value;
	}

	public int getOrUpdate(int key, IntSupplier producer) {
		int value = get(key);

		if (value != NOT_FOUND) {
			return value;
		}

		synchronized (this) {
			value = get(key);

			if (value != NOT_FOUND) {
				return value;
			}

			value = producer.getAsInt();
			put(key, value);
		}

		return value;
	}

	/**
	 * 
	 * @param key
	 *            大于零，小于256k
	 * 
	 * @param value
	 *            大于零
	 */
	public void put(final int key, final int value) {
		if (key < 0) {
			throw new IllegalArgumentException("Illegal key: " + key);
		}

		if (key >= MAXIMUM_CAPACITY) {
			throw new IndexOutOfBoundsException("Illegal key: " + key);
		}

		if (value == -1) {
			throw new IllegalArgumentException("Illegal value: " + value);
		}

		ensureCapacity(key + 1);

		unsafe().putOrderedInt(array, offset(key), value);
	}

	public void clear() {
		if (array == null) {
			return;
		}

		int[] ints = new int[16];
		for (int i = 0; i < ints.length; i++) {
			Arrays.fill(ints, NOT_FOUND);
		}

		array = ints;
	}

	private void ensureCapacity(int capacity) {
		int[] theArray = array;
		if (theArray != null && theArray.length >= capacity) {
			return;
		}

		synchronized (this) {
			int[] finalArray = array;
			if (finalArray != null && finalArray.length >= capacity) {
				return;
			}

			int newCapacity = tableSizeFor(capacity);

			if (newCapacity > MAXIMUM_CAPACITY) {
				throw new IndexOutOfBoundsException(newCapacity);
			}

			int[] ints = new int[newCapacity];
			Arrays.fill(ints, NOT_FOUND);

			if (finalArray != null) {
				System.arraycopy(finalArray, 0, ints, 0, finalArray.length);
			}

			array = ints;
		}
	}

	private static final long offset(int key) {
		return ((long) key << ASHIFT) + ABASE;
	}

	private static final int tableSizeFor(int cap) {
		int n = cap - 1;

		n |= n >>> 1;
		n |= n >>> 2;
		n |= n >>> 4;
		n |= n >>> 8;
		n |= n >>> 16;

		return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
	}

	static {
		try {
			ABASE = unsafe().arrayBaseOffset(int[].class);

			int scale = unsafe().arrayIndexScale(int[].class);
			if ((scale & (scale - 1)) != 0) {
				throw new Error("array index scale not a power of two");
			}

			ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	public static void main(String[] args) {
		ConcurrentIntToIntArrayMap map = new ConcurrentIntToIntArrayMap();
		map.put(16, 16);

		for (int i = 0; i < 1024; i++) {
			map.put(i, i);
		}

		for (int i = 0; i < 1024; i++) {
			System.out.println(i + ":" + map.get(i));
		}
	}

}
