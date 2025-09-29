/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.util.Random;

/**
 * A random number generator for a Pareto distribution
 * 
 * @author Frans Ekman
 */
public class ParetoRNG {
	private Random rng;
	private double xm; // min value (Xm)
	private double k; // coefficient
	private double maxValue;

	/**
	 * Creates a new Pareto random number generator that makes use of a normal
	 * random number generator
	 * 
	 * @param rng
	 * @param k
	 * @param minValue
	 * @param maxValue
	 */
	public ParetoRNG(Random rng, double k, double minValue, double maxValue) {
		this.rng = rng;
		this.xm = minValue;
		this.k = k;
		if (maxValue == -1) {
			this.maxValue = Double.POSITIVE_INFINITY;
		} else {
			this.maxValue = maxValue;
		}
	}

//	/**
//	 * Returns a Pareto distributed double value
//	 *
//	 * @return a Pareto distributed double value
//	 */
//	public double getDouble() {
//		if (xm == -1) {
//			return Double.POSITIVE_INFINITY;
//		}
//		double x;
//		do {
//			x = xm * Math.pow((1 - rng.nextDouble()), (-1 / k));
//		} while (x > maxValue);
//		return x;
//	}
	/**
	 * Returns a Truncated Pareto distributed double value using the
	 * efficient direct inverse transform method.
	 *
	 * @return a Truncated Pareto distributed double value
	 */
	public double getDouble() {
		if (xm == -1) {
			return Double.POSITIVE_INFINITY;
		}

		// Jika maxValue tidak terbatas, gunakan rumus standar non-terpotong
		if (Double.isInfinite(maxValue)) {
			return xm * Math.pow((1 - rng.nextDouble()), (-1 / k));
		}

		double u = rng.nextDouble(); // Angka acak uniform [0, 1]

		double term1 = Math.pow(xm / maxValue, k);
		double term2 = 1.0 - u * (1.0 - term1);

		return xm * Math.pow(term2, -1.0 / k);
	}

}
