package spotifyparser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SpotifyController {

	//<editor-fold defaultstate="collapsed" desc="Login Credentials">
	private static final String SPOTIFY_CLIENT_ID = "fc29eb2aa2fa4bd3ad78a3502c4ff664";
	private static final String SPOTIFY_CLIENT_SECRET = "7a7c852854d943caa50d0ed69bb163ef";
	//</editor-fold>

	private String accessToken;

	/**
	 * Initializes an access token from the Spotify API. This method must be
	 * called before any use of the API.
	 *
	 * @throws java.io.IOException if obtaining the token was not successful.
	 */
	public void authenticate() throws IOException {
		try {
			// Open the connection
			URL requestURL = new URL("https://accounts.spotify.com/api/token");
			HttpURLConnection connection = (HttpURLConnection) requestURL.openConnection();

			// Prepare parameters & data
			String postParameters = "grant_type=client_credentials";
			String keys = SPOTIFY_CLIENT_ID + ":" + SPOTIFY_CLIENT_SECRET;
			String authData = "Basic " + new String(Base64.getEncoder().encode(keys.getBytes()));
			connection.setRequestProperty("Authorization", authData);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);

			// Send parameters & data
			try (OutputStream os = connection.getOutputStream()) {
				os.write(postParameters.getBytes());
			}

			// Read response
			StringBuilder output = new StringBuilder();
			try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				String line;
				while ((line = in.readLine()) != null) {
					output.append(line).append('\n');
				}
			}

			// Parse JSON and extract result
			JsonObject rootObject = new JsonParser().parse(output.toString()).getAsJsonObject();
			this.accessToken = rootObject.get("access_token").getAsString();
		} catch (JsonSyntaxException ex) {
			throw new AssertionError(ex);
		}

	}

	/**
	 * Sends a GET request to the specified URL with the given parameters. This
	 * method takes care of authentification and of replacing space characters
	 * in the parameters string.
	 *
	 * @param url the URL to send the request to.
	 * @param params the parameters to attach. Should not include the preceding
	 * question mark character.
	 * @return the returned JSON data.
	 * @throws IOException if an IO error was encountered.
	 */
	public String sendRequest(String url, String params) throws IOException {
		if (accessToken == null) {
			throw new IOException("Not authenticated.");
		}

		params = params.replace(' ', '+');

		String fullURL = url;
		if (!params.isEmpty()) {
			fullURL += "?" + params;
		}

		// Open connection
		URL requestURL = new URL(fullURL);
		HttpURLConnection connection = (HttpURLConnection) requestURL.openConnection();

		// Prepare parameters & data
		String bearerAuth = "Bearer " + accessToken;
		connection.setRequestProperty("Authorization", bearerAuth);
		connection.setRequestMethod("GET");

		// Get response
		StringBuilder output = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
			String line;
			while ((line = in.readLine()) != null) {
				output.append(line).append('\n');
			}
		}

		return output.toString();
	}

	/**
	 * Acquires the artist ID for a given artist name. If multiple artists are
	 * matched, the first one is selected.
	 *
	 * @param artistNameQuery the artist name. Case-insensitive, and may contain
	 * spaces.
	 * @return the artist ID.
	 * @throws IOException if an I/O error occurred.
	 */
	public String getArtistId(String artistNameQuery) throws IOException {
		try {
			// Prepare and send query
			String endpoint = "https://api.spotify.com/v1/search";
			String params = "market=CA&type=artist&q=" + artistNameQuery;
			String jsonOutput = sendRequest(endpoint, params);

			// Parse result
			JsonObject obj = new JsonParser().parse(jsonOutput).getAsJsonObject();
			return obj.get("id").getAsString();
		} catch (JsonSyntaxException ex) {
			throw new AssertionError(ex);
		}
	}

	/**
	 * Acquires the album IDs of a given artist.
	 *
	 * @param artistId the ID of the artist.
	 * @return a list of IDs corresponding to each album.
	 * @throws java.io.IOException if an I/O error occurs.
	 */
	public List<String> getAlbumIds(String artistId) throws IOException {
		List<String> ids = new ArrayList<>();

		try {
			// Prepare and send query
			String endpoint = String.format("https://api.spotify.com/v1/artists/%s/albums", artistId);
			String params = "market=CA&limit=50";
			String jsonOutput = sendRequest(endpoint, params);

			// Parse result
			JsonObject obj = new JsonParser().parse(jsonOutput).getAsJsonObject();
			obj.get("items").getAsJsonArray().forEach(elm -> {
				ids.add(elm.getAsJsonObject().get("id").getAsString());
			});

		} catch (JsonSyntaxException ex) {
			throw new AssertionError(ex);
		}

		return ids;
	}

	/**
	 * Retrieves album data for a given ID.
	 *
	 * @param albumId the ID of the album.
	 * @return the associated album object.
	 * @throws IOException if an I/O occurs.
	 */
	public Album getAlbumData(String albumId) throws IOException {

		try {
			// Prepare and send query
			String endpoint = String.format("https://api.spotify.com/v1/albums/%s", albumId);
			String params = "market=CA";
			String jsonOutput = sendRequest(endpoint, params);

			// Parse result
			JsonObject obj = new JsonParser().parse(jsonOutput).getAsJsonObject();
			return parseAlbumJson(obj);
		} catch (JsonSyntaxException ex) {
			throw new AssertionError(ex);
		}
	}

	/**
	 * Retrieves album data for a given list of IDs. This method uses only one
	 * API request and is therefore faster than calling
	 * {@link #getAlbumData(java.lang.String)} for each single album.
	 *
	 * @param albumIds the list of album IDs.
	 * @return the associated album objects, in the order supplied.
	 * @throws IOException if an I/O occurs.
	 */
	public List<Album> getAlbumsData(List<String> albumIds) throws IOException {

		List<Album> albums = new ArrayList<>();
		
		try {
			// Prepare and send query
			String str = albumIds.toString();
			str = str.substring(1, str.length() - 1);

			String endpoint = "https://api.spotify.com/v1/albums";
			String params = "market=CA&" + str;
			String jsonOutput = sendRequest(endpoint, params);

			// Parse result
			JsonObject obj = new JsonParser().parse(jsonOutput).getAsJsonObject();
			obj.get("albums").getAsJsonArray().forEach(elmnt -> {
				albums.add(parseAlbumJson(elmnt.getAsJsonObject()));
			});
			
			return albums;
		} catch (JsonSyntaxException ex) {
			throw new AssertionError(ex);
		}
	}

	private Album parseAlbumJson(JsonObject obj) {
		String albumName = obj.get("name").getAsString();
		String artistName = obj.get("artists").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString();
		String coverImageURL = obj.get("images").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();

		List<TrackData> tracks = new ArrayList<>();
		JsonArray tracksArray = obj.get("tracks").getAsJsonObject().get("items").getAsJsonArray();
		for (JsonElement elmnt : tracksArray) {
			JsonObject trackObj = elmnt.getAsJsonObject();
			String trackName = trackObj.get("name").getAsString();
			String trackId = trackObj.get("id").getAsString();
			int length = trackObj.get("duration_ms").getAsInt() / 1000;

			tracks.add(new TrackData(trackName, trackId, length));
		}

		return new Album(artistName, albumName, coverImageURL, tracks);
	}

}
