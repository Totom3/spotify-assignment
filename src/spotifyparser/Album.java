package spotifyparser;

import java.util.List;
import javafx.scene.image.Image;

public class Album {

	private final String artistName;
	private final String albumName;
	private final Image coverImage;
	private final List<TrackData> tracks;

	public Album(String artistName, String albumName, Image image, List<TrackData> tracks) {
		this.artistName = artistName;
		this.albumName = albumName;
		this.coverImage = image;
		this.tracks = tracks;
	}

	public String getArtistName() {
		return artistName;
	}

	public String getAlbumName() {
		return albumName;
	}

	public Image getCoverImage() {
		return coverImage;
	}

	public List<TrackData> getTracks() {
		return tracks;
	}
}
