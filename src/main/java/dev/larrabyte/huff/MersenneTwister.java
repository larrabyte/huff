package dev.larrabyte.huff;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public strictfp class MersenneTwister implements Persistable, Cloneable {
    private static final long serialVersionUID = -8219700664442619525L;

    // Period parameters.
    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIX_A = 0x9908B0DF;
    private static final int UPPER_MASK = 0x80000000;
    private static final int LOWER_MASK = 0x7FFFFFFF;

    // Tempering parameters.
    private static final int TEMPERING_MASK_B = 0x9D2C5680;
    private static final int TEMPERING_MASK_C = 0xEFC60000;

    private int mt[];
    private int mti;
    private int mag01[];

    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public Object clone() {
        try {
            MersenneTwister f = (MersenneTwister) super.clone();
            f.mag01 = (int[]) mag01.clone();
            f.mt = (int[]) mt.clone();
            return f;
        } catch (CloneNotSupportedException e) {
            // This should never happen!
            throw new InternalError();
        }
    }

    public boolean stateEquals(Object o) {
        if (o == this) return true;
        if (o == null || !(o instanceof MersenneTwister)) return false;

        MersenneTwister other = (MersenneTwister) o;
        if (mti != other.mti) return false;

        for (int x = 0; x < mag01.length; x++) {
            if (mag01[x] != other.mag01[x]) return false;
        }

        for (int x = 0; x < mt.length; x++) {
            if (mt[x] != other.mt[x]) return false;
        }

        return true;
    }

    // Reads the state of a Mersenne Twister from an input stream.
    public void readState(DataInputStream stream) throws IOException {
        for (int x = 0; x < mt.length; x++) {
            mt[x] = stream.readInt();
        }

        for (int x = 0; x < mag01.length; x++) {
            mag01[x] = stream.readInt();
        }

        mti = stream.readInt();
        nextNextGaussian = stream.readDouble();
        haveNextNextGaussian = stream.readBoolean();
    }

    // Writes the state of the Mersenne Twister to an output stream.
    public void writeState(DataOutputStream stream) throws IOException {
        for (int x = 0; x < mt.length; x++) {
            stream.writeInt(mt[x]);
        }

        for (int x = 0; x < mag01.length; x++) {
            stream.writeInt(mag01[x]);
        }

        stream.writeInt(mti);
        stream.writeDouble(nextNextGaussian);
        stream.writeBoolean(haveNextNextGaussian);
    }

    // Constructor (uses time as the default seed).
    public MersenneTwister() {
        this(System.currentTimeMillis());
    }

    // Constructor using a given seed (best if it remains within integer limits).
    public MersenneTwister(long seed) {
        setSeed(seed);
    }

    // Constructor using an array of integers. Only the first 624 are used.
    public MersenneTwister(int[] array) {
        setSeed(array);
    }

    // Initialise the pseudo-random number generator (only first 32-bits of the seed).
    synchronized public void setSeed(long seed) {
        // Due to a bug in java.util.Random clear up to 1.2, we're doing our own Gaussian variable.
        haveNextNextGaussian = false;

        mt = new int[N];
        mag01 = new int[2];
        mag01[0] = 0x0;
        mag01[1] = MATRIX_A;

        mt[0] = (int) (seed & 0xFFFFFFFF);
        for (mti = 1; mti < N; mti++) {
            mt[mti] = (1812433253 * (mt[mti - 1] ^ (mt[mti - 1] >>> 30)) + mti);
        }
    }

    // Sets the seed of the Mersenne Twister using an array of integers.
    // Only the first 624 integers in the array are used.
    synchronized public void setSeed(int[] array) {
        if (array.length == 0) {
            throw new IllegalArgumentException("Array length must be greater than zero");
        }

        // Dunno how this shit works,
        // pure maths from here on out.
        int i = 1;
        int j = 0;
        setSeed(19650218);

        for (int k = N > array.length ? N : array.length; k != 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525)) + array[j] + j;
            i++;
            j++;

            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }

            if (j >= array.length) {
                j = 0;
            }
        }

        for (int k = N - 1; k != 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941)) - i;
            i++;

            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
        }

        // MSB is 1, assuring a non-zero initial array.
        mt[0] = 0x80000000;
    }

    public int nextInt() {
        int y;

        // Generate N words at a time.
        if (mti >= N) {
            int kk = 0;

            for (; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        return y;
    }

    public short nextShort() {
        int y;

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        return (short) (y >>> 16);
    }

    public char nextChar() {
        int y;

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        return (char) (y >>> 16);
    }

    public boolean nextBoolean() {
        int y;

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        return (boolean) ((y >>> 31) != 0);
    }

    // Generates a boolean with a probability of returning true.
    public boolean nextBoolean(float probability) {
        int y;

        if (probability < 0.0f || probability > 1.0f) {
            throw new IllegalArgumentException("probability must be between 0.0 and 1.0 inclusive.");
        }

        if (probability == 0.0f) {
            return false;
        } else if (probability == 1.0f) {
            return true;
        }

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        return (y >>> 8) / ((float) (1 << 24)) < probability;
    }

    // Generates a boolean with a probability of returning true.
    public boolean nextBoolean(double probability) {
        int y;
        int z;

        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be between 0.0 and 1.0 inclusive.");
        }

        if (probability == 0.0) {
            return false;
        } else if (probability == 1.0) {
            return true;
        }

        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        // Generate N words at one time
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (z >>> 1) ^ mag01[z & 0x1];
            }

            for (; kk < N - 1; kk++) {
                z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (z >>> 1) ^ mag01[z & 0x1];
            }

            z = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (z >>> 1) ^ mag01[z & 0x1];
            mti = 0;
        }

        z = mt[mti++];
        z ^= z >>> 11;
        z ^= (z << 7) & TEMPERING_MASK_B;
        z ^= (z << 15) & TEMPERING_MASK_C;
        z ^= (z >>> 18);

        /* derived from nextDouble documentation in jdk 1.2 docs, see top */
        return ((((long) (y >>> 6)) << 27) + (z >>> 5)) / (double) (1L << 53) < probability;
    }

    public byte nextByte() {
        int y;

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        return (byte) (y >>> 24);
    }

    public void nextBytes(byte[] bytes) {
        int y;

        for (int x = 0; x < bytes.length; x++) {
            // Generate N words at one time.
            if (mti >= N) {
                int kk;

                for (kk = 0; kk < N - M; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                for (; kk < N - 1; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
                mti = 0;
            }

            y = mt[mti++];
            y ^= y >>> 11;
            y ^= (y << 7) & TEMPERING_MASK_B;
            y ^= (y << 15) & TEMPERING_MASK_C;
            y ^= (y >>> 18);

            bytes[x] = (byte) (y >>> 24);
        }
    }

    public long nextLong() {
        int y;
        int z;

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (z >>> 1) ^ mag01[z & 0x1];
            }

            for (; kk < N - 1; kk++) {
                z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (z >>> 1) ^ mag01[z & 0x1];
            }

            z = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (z >>> 1) ^ mag01[z & 0x1];
            mti = 0;
        }

        z = mt[mti++];
        z ^= z >>> 11;
        z ^= (z << 7) & TEMPERING_MASK_B;
        z ^= (z << 15) & TEMPERING_MASK_C;
        z ^= (z >>> 18);

        return (((long) y) << 32) + (long) z;
    }

    // Returns a uniformly random number from 0 to n-1.
    public long nextLong(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive, got: " + n);
        }

        long bits, val;
        int y, z;

        do {
            // Generate N words at one time.
            if (mti >= N) {
                int kk;

                for (kk = 0; kk < N - M; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                for (; kk < N - 1; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
                mti = 0;
            }

            y = mt[mti++];
            y ^= y >>> 11;
            y ^= (y << 7) & TEMPERING_MASK_B;
            y ^= (y << 15) & TEMPERING_MASK_C;
            y ^= (y >>> 18);

            // Generate N words at one time.
            if (mti >= N) {
                int kk;

                for (kk = 0; kk < N - M; kk++) {
                    z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + M] ^ (z >>> 1) ^ mag01[z & 0x1];
                }

                for (; kk < N - 1; kk++) {
                    z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + (M - N)] ^ (z >>> 1) ^ mag01[z & 0x1];
                }

                z = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                mt[N - 1] = mt[M - 1] ^ (z >>> 1) ^ mag01[z & 0x1];
                mti = 0;
            }

            z = mt[mti++];
            z ^= z >>> 11;
            z ^= (z << 7) & TEMPERING_MASK_B;
            z ^= (z << 15) & TEMPERING_MASK_C;
            z ^= (z >>> 18);

            bits = (((((long) y) << 32) + (long) z) >>> 1);
            val = bits % n;
        } while (bits - val + (n - 1) < 0);

        return val;
    }

    // Returns a random double in the range [0.0, 1.0).
    public double nextDouble() {
        int y;
        int z;

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (z >>> 1) ^ mag01[z & 0x1];
            }

            for (; kk < N - 1; kk++) {
                z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (z >>> 1) ^ mag01[z & 0x1];
            }

            z = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (z >>> 1) ^ mag01[z & 0x1];
            mti = 0;
        }

        z = mt[mti++];
        z ^= z >>> 11;
        z ^= (z << 7) & TEMPERING_MASK_B;
        z ^= (z << 15) & TEMPERING_MASK_C;
        z ^= (z >>> 18);

        // Derived from nextDouble documentation in jdk 1.2 docs, see top.
        return ((((long) (y >>> 6)) << 27) + (z >>> 5)) / (double) (1L << 53);
    }

    // Returns a double between 0.0 and 1.0, potentially inclusive.
    public double nextDouble(boolean includeZero, boolean includeOne) {
        double d = 0.0;

        do {
            // Grab a value, initially from half-open [0.0, 1.0).
            d = nextDouble();

             // If including one, push to [1.0, 2.0).
            if (includeOne && nextBoolean()) {
                d += 1.0;
            }
        } while ((d > 1.0) || (!includeZero && d == 0.0));

        return d;
    }

    public double nextGaussian() {
        if(haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }

        else {
            double v1, v2, s;

            do {
                int a, b, y, z;

                // Generate N words at one time.
                if (mti >= N) {
                    int kk;

                    for (kk = 0; kk < N - M; kk++) {
                        y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
                    }

                    for (; kk < N - 1; kk++) {
                        y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
                    }

                    y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                    mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
                    mti = 0;
                }

                y = mt[mti++];
                y ^= y >>> 11;
                y ^= (y << 7) & TEMPERING_MASK_B;
                y ^= (y << 15) & TEMPERING_MASK_C;
                y ^= (y >>> 18);

                // Generate N words at one time.
                if (mti >= N) {
                    int kk;

                    for (kk = 0; kk < N - M; kk++) {
                        z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + M] ^ (z >>> 1) ^ mag01[z & 0x1];
                    }

                    for (; kk < N - 1; kk++) {
                        z = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + (M - N)] ^ (z >>> 1) ^ mag01[z & 0x1];
                    }

                    z = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                    mt[N - 1] = mt[M - 1] ^ (z >>> 1) ^ mag01[z & 0x1];
                    mti = 0;
                }

                z = mt[mti++];
                z ^= z >>> 11;
                z ^= (z << 7) & TEMPERING_MASK_B;
                z ^= (z << 15) & TEMPERING_MASK_C;
                z ^= (z >>> 18);

                // Generate N words at one time.
                if (mti >= N) {
                    int kk;

                    for (kk = 0; kk < N - M; kk++) {
                        a = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + M] ^ (a >>> 1) ^ mag01[a & 0x1];
                    }

                    for (; kk < N - 1; kk++) {
                        a = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + (M - N)] ^ (a >>> 1) ^ mag01[a & 0x1];
                    }

                    a = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                    mt[N - 1] = mt[M - 1] ^ (a >>> 1) ^ mag01[a & 0x1];
                    mti = 0;
                }

                a = mt[mti++];
                a ^= a >>> 11;
                a ^= (a << 7) & TEMPERING_MASK_B;
                a ^= (a << 15) & TEMPERING_MASK_C;
                a ^= (a >>> 18);

                // Generate N words at one time.
                if (mti >= N) {
                    int kk;

                    for (kk = 0; kk < N - M; kk++) {
                        b = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + M] ^ (b >>> 1) ^ mag01[b & 0x1];
                    }

                    for (; kk < N - 1; kk++) {
                        b = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                        mt[kk] = mt[kk + (M - N)] ^ (b >>> 1) ^ mag01[b & 0x1];
                    }

                    b = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                    mt[N - 1] = mt[M - 1] ^ (b >>> 1) ^ mag01[b & 0x1];
                    mti = 0;
                }

                b = mt[mti++];
                b ^= b >>> 11;
                b ^= (b << 7) & TEMPERING_MASK_B;
                b ^= (b << 15) & TEMPERING_MASK_C;
                b ^= (b >>> 18);

                // Derived from nextDouble documentation in jdk 1.2 docs, see top.
                v1 = 2 * (((((long) (y >>> 6)) << 27) + (z >>> 5)) / (double) (1L << 53)) - 1;
                v2 = 2 * (((((long) (a >>> 6)) << 27) + (b >>> 5)) / (double) (1L << 53)) - 1;
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);

            double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
            nextNextGaussian = v2 * multiplier;
            haveNextNextGaussian = true;
            return v1 * multiplier;
        }
    }

    // Returns a random float from [0.0f, 1.0f).
    public float nextFloat() {
        int y;

        // Generate N words at one time.
        if (mti >= N) {
            int kk;

            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
            }

            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
            mti = 0;
        }

        y = mt[mti++];
        y ^= y >>> 11;
        y ^= (y << 7) & TEMPERING_MASK_B;
        y ^= (y << 15) & TEMPERING_MASK_C;
        y ^= (y >>> 18);

        return (y >>> 8) / ((float) (1 << 24));
    }

    // Returns a float in the range from 0.0f to 1.0f, possibly inclusive of 0.0f and 1.0f themselves.
    public float nextFloat(boolean includeZero, boolean includeOne) {
        float d = 0.0f;

        do {
            // Grab a value, initially from half-open [0.0f, 1.0f).
            d = nextFloat();

            // If including one, push to [1.0f, 2.0f).
            if (includeOne && nextBoolean()) {
                d += 1.0f;
            }
        } while ((d > 1.0f) || (!includeZero && d == 0.0f));

        return d;
    }

    // Returns a uniformly random number from 0 to n-1.
    public int nextInt(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive, got: " + n);
        }

        // i.e. n is a power of 2.
        if ((n & -n) == n) {
            int y;

            // Generate N words at one time.
            if (mti >= N) {
                int kk;

                for (kk = 0; kk < N - M; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                for (; kk < N - 1; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
                mti = 0;
            }

            y = mt[mti++];
            y ^= y >>> 11;
            y ^= (y << 7) & TEMPERING_MASK_B;
            y ^= (y << 15) & TEMPERING_MASK_C;
            y ^= (y >>> 18);

            return (int) ((n * (long) (y >>> 1)) >> 31);
        }

        int bits, val, y;

        do {
            // Generate N words at one time.
            if (mti >= N) {
                int kk;

                for (kk = 0; kk < N - M; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                for (; kk < N - 1; kk++) {
                    y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                    mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
                }

                y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
                mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];
                mti = 0;
            }

            y = mt[mti++];
            y ^= y >>> 11;
            y ^= (y << 7) & TEMPERING_MASK_B;
            y ^= (y << 15) & TEMPERING_MASK_C;
            y ^= (y >>> 18);

            bits = (y >>> 1);
            val = bits % n;
        } while (bits - val + (n - 1) < 0);

        return val;
    }

    // Returns an integer drawn uniformly from a to b-1.
    public int nextInt(int a, int b) {
        if (b <= a) {
            String reason = String.format("b must be greater than a! (a: %d, b: %d)", a, b);
            throw new IllegalArgumentException(reason);
        }

        return this.nextInt(b - a) + a;
    }

    // Returns an integer drawn uniformly from a to b-1.
    public long nextLong(long a, long b) {
        if (b <= a) {
            String reason = String.format("b must be greater than a! (a: %d, b: %d)", a, b);
            throw new IllegalArgumentException(reason);
        }

        return this.nextLong(b - a) + a;
    }

    // Returns a floating-point number in the range of [a, b).
    public float nextFloat(float a, float b) {
        if (b <= a) {
            String reason = String.format("b must be greater than a! (a: %f, b: %f)", a, b);
            throw new IllegalArgumentException(reason);
        }

        float fractional = this.nextFloat() * (b - a);
        return a + fractional;
    }

    // Returns a double floating-point number in the range of [a, b).
    public double nextDouble(double a, double b) {
        if (b <= a) {
            String reason = String.format("b must be greater than a! (a: %f, b: %f)", a, b);
            throw new IllegalArgumentException(reason);
        }

        double fractional = this.nextDouble() * (b - a);
        return a + fractional;
    }
}
