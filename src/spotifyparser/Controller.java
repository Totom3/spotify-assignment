package spotifyparser;

import com.sun.javafx.collections.ObservableListWrapper;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import javax.imageio.ImageIO;

/**
 *
 * @author Tomer Moran
 * @author Hany Albouz
 */
public class Controller implements Initializable {

	@FXML
	private TableView<TrackData> tracksTableView;

	@FXML
	private Label artistLabel;

	@FXML
	private Label albumLabel;

	@FXML
	private Label durationLabel;

	@FXML
	private Button playButton;

	@FXML
	private Button previousButton;

	@FXML
	private Button nextButton;

	@FXML
	private Slider trackSlider;

	@FXML
	private TextField searchField;

	@FXML
	private ImageView albumCoverImageView;

	@FXML
	private ProgressIndicator progressIndicator;

	private String artistName;
	private List<Album> albums;
	private MediaPlayer mediaPlayer;
	private final SpotifyAPI controller;
	private ScheduledExecutorService generalExecutor;

	private int currentAlbumIndex;
	private TrackData currentlyPlayed;
	private final WeakHashMap<TrackData, Button> tablePlayButtons = new WeakHashMap<>();
	private ScheduledFuture<?> updateSliderTask;

	private volatile boolean savingImages;

	public Controller() {
		this.controller = new SpotifyAPI();
		this.generalExecutor = Executors.newSingleThreadScheduledExecutor();

		try {
			controller.authenticate();
		} catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}

	private void handlePlayButton(TrackData track) {
		if (currentlyPlayed == null) {
			playPreview(track);
			this.currentlyPlayed = track;
			return;
		}

		// If a track is being played, stop the previous track
		mediaPlayer.stop();
		onStopTrack(currentlyPlayed);

		// Simply reset the fields if stopping the preview
		if (currentlyPlayed == track) {
			this.mediaPlayer = null;
			this.currentlyPlayed = null;
			return;
		}

		// Playing a different track
		playPreview(track);
		this.currentlyPlayed = track;
	}

	private void playPreview(TrackData track) {
		if (mediaPlayer != null) {
			mediaPlayer.stop();
		}

		// Update buttons
		playButton.setText("Stop");
		getTrackPlayButton(track).setText("Stop");

		// Update slider
		trackSlider.setValue(0);
		trackSlider.setDisable(false);

		// Initialize media player
		Media music = new Media(track.getPreviewURL());
		mediaPlayer = new MediaPlayer(music);
		mediaPlayer.play();

		// Handle end of song or stop
		mediaPlayer.setOnEndOfMedia(() -> onStopTrack(track));

		// Cancel previous task
		if (updateSliderTask != null) {
			updateSliderTask.cancel(false);
			updateSliderTask = null;
		}

		// Schedule the slider to move every second
		updateSliderTask = generalExecutor.scheduleAtFixedRate(() -> {
			executeSync(() -> {
				// Move slider
				trackSlider.setValue(trackSlider.getValue() + 1.0);
			});
		}, 1, 1, TimeUnit.SECONDS);

		int minutes = track.getLength() / 60;
		int seconds = track.getLength() % 60;
		durationLabel.setText("0:0/" + minutes + ":" + seconds);
	}

	private void onStopTrack(TrackData track) {
		playButton.setText("Play");

		if (track != null) {
			getTrackPlayButton(track).setText("Play");
		}

		if (updateSliderTask != null) {
			trackSlider.setDisable(true);
			updateSliderTask.cancel(false);
			updateSliderTask = null;
		}
	}

	private void searchArtist(ActionEvent ae) {
		progressIndicator.setVisible(true);
		executeAsync(() -> {
			boolean success = loadArtist(searchField.getText());
			progressIndicator.setVisible(false);

			if (success) {
				if (!albums.isEmpty()) {
					// Update display
					executeSync(() -> displayAlbum(0));
				} else {
					// Artist has no album: disable everything
					executeSync(() -> {
						artistLabel.setText(artistName);
						albumLabel.setText("No album for this artist");
						playButton.setDisable(true);
						nextButton.setDisable(true);
						previousButton.setDisable(true);
					});
				}
			} else {
				// Could not find artist
				executeSync(() -> {
					artistLabel.setText("Error: Artist not found");
					albumLabel.setText("Try again with another artist");
					playButton.setDisable(true);
					nextButton.setDisable(true);
					previousButton.setDisable(true);
				});
			}

		});
	}

	private void previousAlbum(ActionEvent ae) {
		displayAlbum(--currentAlbumIndex);
	}

	private void nextAlbum(ActionEvent ae) {
		displayAlbum(++currentAlbumIndex);
	}

	public void saveMenuHandle(ActionEvent ae) {
		if (savingImages)
			return;

		savingImages = true;
		progressIndicator.setVisible(true);
		executeAsync(() -> {
			for (Album album : albums) {
				// Ensure the path exists

				// Replace special characters 
				String name = album.getAlbumName().replaceAll("[:/^.\\*?\"<>|]", " ");
				File path = new File("./images/" + artistName + "/" + name + ".png");
				if (!path.getParentFile().isDirectory()) {
					try {
						path.mkdirs();
					} catch (SecurityException se) {
						// Skip if could not create folder
						System.err.println("Could not create folder " + path.getAbsolutePath());
						se.printStackTrace(System.err);
						continue;
					}
				}

				// Write file
				Image image = album.getCoverImage();
				BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
				try {
					ImageIO.write(bufferedImage, "png", path);
				} catch (IOException ex) {
					System.err.println("Could not save image " + path.getAbsolutePath());
					ex.printStackTrace(System.err);
				}

			}

			executeSync(() -> progressIndicator.setVisible(false));
			savingImages = false;

			System.out.println("Saved album images for " + artistName);

		});

	}

	private boolean loadArtist(String artistName) {
		try {
			String artistId = controller.getArtistId(artistName);
			if (artistId == null) {
				this.artistName = "";
				this.albums = new ArrayList<>();
				return false;
			}

			List<String> albumIds = controller.getAlbumIds(artistId);
			List<Album> data = controller.getAlbumsData(albumIds);

			this.artistName = artistName;
			this.albums = data;
			return true;
		} catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}

	private void displayAlbum(int number) {
		// Update data
		this.currentAlbumIndex = number;
		Album album = albums.get(number);

		// Set tracks
		tracksTableView.setItems(new ObservableListWrapper(album.getTracks()));

		// Setup slider
		trackSlider.setValue(0.0);
		trackSlider.setDisable(true);

		// Update labels
		artistLabel.setText(this.artistName);
		albumLabel.setText(album.getAlbumName());

		// Update album cover
		albumCoverImageView.setImage(album.getCoverImage());

		// Enable or disable the buttons
		previousButton.setDisable(currentAlbumIndex == 0);
		nextButton.setDisable(currentAlbumIndex == albums.size() - 1);
	}

	@Override
	public void initialize(URL url, ResourceBundle rb) {

		// Setup Table View
		TableColumn<TrackData, Number> trackNumberColumn = new TableColumn("#");
		trackNumberColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getTrackNumber()));

		TableColumn<TrackData, String> trackTitleColumn = new TableColumn("Title");
		trackTitleColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getName()));
		trackTitleColumn.setPrefWidth(250);

		TableColumn<TrackData, Button> playColumn = new TableColumn("Preview");
		playColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(getTrackPlayButton(p.getValue())));
		tracksTableView.getColumns().setAll(trackNumberColumn, trackTitleColumn, playColumn);

		trackNumberColumn.setPrefWidth(30);
		trackTitleColumn.setMaxWidth(230);

		// When slider is released, we must seek in the song
		trackSlider.setOnMouseReleased(event -> {
			if (mediaPlayer != null) {
				mediaPlayer.seek(Duration.seconds(trackSlider.getValue()));
			}
		});

		progressIndicator.setVisible(false);

		nextButton.setOnAction(this::nextAlbum);
		previousButton.setOnAction(this::previousAlbum);
		searchField.setOnAction(this::searchArtist);

		playButton.setOnAction(ea -> {
			ObservableList<TrackData> selected = tracksTableView.getSelectionModel().getSelectedItems();

			TrackData track;
			if (currentlyPlayed != null) {
				track = currentlyPlayed;
			} else if (selected.isEmpty()) {
				track = albums.get(currentAlbumIndex).getTracks().get(0);
				tracksTableView.getSelectionModel().clearAndSelect(track.getTrackNumber() - 1);
			} else {
				track = selected.get(0);
			}

			handlePlayButton(track);
		});

		// Initialize GUI
		if (loadArtist("Kurt Elling") && !albums.isEmpty()) {
			executeSync(() -> displayAlbum(0));
		}

	}

	private void executeAsync(Runnable r) {
		generalExecutor.schedule(r, 0, TimeUnit.SECONDS);
	}

	private void executeSync(Runnable r) {
		Platform.runLater(r);
	}

	private Button getTrackPlayButton(TrackData track) {
		Button button = tablePlayButtons.get(track);
		if (button != null) {
			return button;
		}

		button = new Button("Play");
		if (track == null || !track.hasPreview()) {
			button.setDisable(true);
		} else {
			button.setDisable(false);
			button.setOnAction(event -> {
				handlePlayButton(track);
			});
		}

		tablePlayButtons.put(track, button);
		return button;
	}
}
