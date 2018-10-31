package spotifyparser;

import com.sun.javafx.collections.ObservableListWrapper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Callback;
import javafx.util.Duration;

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
	private final SpotifyController controller;
	private ScheduledExecutorService sliderExecutor;
	private ScheduledExecutorService generalExecutor;

	private int currentAlbumIndex;
	private boolean isPlayingPreview;

	public Controller() {
		this.controller = new SpotifyController();
		this.generalExecutor = Executors.newSingleThreadScheduledExecutor();

		try {
			controller.authenticate();
		} catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}

	private void playPauseTrackPreview(TrackData data) {
		if (source.getText().equals("Play")) {
				if (mediaPlayer != null) {
					mediaPlayer.stop();
				}

				source.setText("Stop");
				trackSlider.setDisable(false);
				trackSlider.setValue(0.0);

				// Start playing music
				Media music = new Media(trackPreviewUrl);
				mediaPlayer = new MediaPlayer(music);
				mediaPlayer.play();

				// This runnable object will be called
				// when the track is finished or stopped
				Runnable stopTrackRunnable = () -> {
					source.setText("Play");
					if (sliderExecutor != null) {
						sliderExecutor.shutdownNow();
					}
				};
				mediaPlayer.setOnEndOfMedia(stopTrackRunnable);
				mediaPlayer.setOnStopped(stopTrackRunnable);

				// Schedule the slider to move right every second
				sliderExecutor = Executors.newSingleThreadScheduledExecutor();
				sliderExecutor.scheduleAtFixedRate(new Runnable() {
					@Override
					public void run() {
						// We can't update the GUI elements on a separate thread... 
						// Let's call Platform.runLater to do it in main thread!!
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								// Move slider
								trackSlider.setValue(trackSlider.getValue() + 1.0);
							}
						});
					}
				}, 1, 1, TimeUnit.SECONDS);
			} else {
				if (mediaPlayer != null) {
					mediaPlayer.stop();
				}
			}
		
	}

	private void searchArtist(ActionEvent ae) {
		progressIndicator.setVisible(true);
		executeAsync(() -> {
			loadArtist(searchField.getText());
			progressIndicator.setVisible(false);

			if (!albums.isEmpty()) {
				executeSync(() -> displayAlbum(0));
			}
		});
	}

	private void previousAlbum(ActionEvent ae) {
		displayAlbum(--currentAlbumIndex);
	}

	private void nextAlbum(ActionEvent ae) {
		displayAlbum(++currentAlbumIndex);
	}

	private void loadArtist(String artistName) {
		try {
			String artistId = controller.getArtistId(artistName);
			List<String> albumIds = controller.getAlbumIds(artistId);
			List<Album> data = controller.getAlbumsData(albumIds);

			this.currentAlbumIndex = 0;
			this.artistName = artistName;
			this.albums = data;
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

		TableColumn<TrackData, TrackData> playColumn = new TableColumn("Preview");
		playColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue()));
		Callback<TableColumn<TrackData, TrackData>, TableCell<TrackData, TrackData>> cellFactory = column -> new PlayTableCell();
		playColumn.setCellFactory(cellFactory);
		tracksTableView.getColumns().setAll(trackNumberColumn, trackTitleColumn, playColumn);

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

		// Initialize GUI
		executeAsync(() -> {
			loadArtist("Pink Floyd");

			if (!albums.isEmpty()) {
				executeSync(() -> displayAlbum(0));
			}
		});
	}

	private void executeAsync(Runnable r) {
		generalExecutor.schedule(r, 0, TimeUnit.SECONDS);
	}

	private void executeSync(Runnable r) {
		Platform.runLater(r);
	}

	private class PlayTableCell extends TableCell<TrackData, TrackData> {

		final Button playButton = new Button("Play");

		@Override
		public void updateItem(TrackData track, boolean empty) {
			setGraphic(playButton);
			setText(null);

			if (track == null || !track.hasPreview()) {
				playButton.setDisable(true);
			} else {
				playButton.setDisable(false);
				playButton.setOnAction(event -> {
					playPauseTrackPreview(track);
				});

			}

		}
	}
}
