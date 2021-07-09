package dev.larrabyte.huff;

public final class WaitTimer {
	// Time storage.
	private long time;

	// Constructor (time specified in nanoseconds).
	public WaitTimer() {
		this.time = System.nanoTime();
	}

	public void reset() {
		this.time = System.nanoTime();
	}

	public long getTime() {
		return System.nanoTime() - this.time;
	}

	public boolean hasTimeElapsed(long time) {
		return getTime() >= time;
	}
}
