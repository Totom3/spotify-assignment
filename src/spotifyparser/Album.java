package spotifyparser;

import java.util.List;

public class Album {

	private final String artistName;
	private final String albumName;
	private final String coverImageURL;
	private final List<TrackData> tracks;

	public Album(String artistName, String albumName, String imageURL, List<TrackData> tracks) {
		this.artistName = artistName;
		this.albumName = albumName;
		this.coverImageURL = imageURL;
		this.tracks = tracks;
	}

	public String getArtistName() {
		return artistName;
	}

	public String getAlbumName() {
		return albumName;
	}

	public String getCoverImageURL() {
		return coverImageURL;
	}

	public List<TrackData> getTracks() {
		return tracks;
	}
}
