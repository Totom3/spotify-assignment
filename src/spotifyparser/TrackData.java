package spotifyparser;

import java.util.Objects;

public class TrackData {

	private final String name;
	private final String id;
	private final int length;

	public TrackData(String name, String id, int length) {
		this.name = name;
		this.id = id;
		this.length = length;
	}

	public String getName() {
		return name;
	}

	public String getId() {
		return id;
	}

	public int getLength() {
		return length;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 97 * hash + Objects.hashCode(this.name);
		hash = 97 * hash + Objects.hashCode(this.id);
		hash = 97 * hash + this.length;
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (!(obj instanceof TrackData)) {
			return false;
		}

		TrackData other = (TrackData) obj;
		return this.name.equals(other.name)
				&& this.id.equals(other.id);
	}

}
