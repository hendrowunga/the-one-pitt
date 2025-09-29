package core;

import java.io.Serializable;
import java.util.Objects;

/**
 * Kelas Tuple generik yang robust.
 * Mengimplementasikan equals(), hashCode(), dan Serializable agar bisa
 * digunakan secara andal dalam struktur data seperti HashMap dan untuk persistensi.
 *
 * @param <K> Tipe elemen pertama (key/first)
 * @param <V> Tipe elemen kedua (value/second)
 */
public class Tuple<K, V> implements Serializable {
	private static final long serialVersionUID = 1L; // Untuk serialisasi

	private final K key;
	private final V value;

	public Tuple(K key, V value) {
		this.key = key;
		this.value = value;
	}

	public K getKey() {
		return key;
	}

	public V getValue() {
		return value;
	}

	// Metode alternatif yang lebih generik
	public K getFirst() {
		return key;
	}

	public V getSecond() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Tuple<?, ?> tuple = (Tuple<?, ?>) o;
		return Objects.equals(key, tuple.key) &&
				Objects.equals(value, tuple.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, value);
	}

	@Override
	public String toString() {
		return "(" + key + ", " + value + ")";
	}
}