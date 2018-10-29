/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package spotifyparser;

import com.sun.javafx.collections.ObservableListWrapper;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Callback;
import javafx.util.Duration;

/**
 *
 * @author bergeron
 */
public class Controller implements Initializable {

	@FXML
	private TableView tracksTableView;

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

	private String artistName;
	private List<Album> albums;
	private MediaPlayer mediaPlayer;
	private final SpotifyController controller;
	private ScheduledExecutorService sliderExecutor;

	private int currentAlbumIndex = 0;

	public Controller() {
		this.controller = new SpotifyController();

		try {
			controller.authenticate();
		} catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}

	private void playPauseTrackPreview(Button source, String trackPreviewUrl) {
		try {
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
				Runnable stopTrackRunnable = new Runnable() {
					@Override
					public void run() {
						source.setText("Play");
						if (sliderExecutor != null) {
							sliderExecutor.shutdownNow();
						}
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
		} catch (Exception e) {
			System.err.println("error with slider executor... this should not happen!");
		}
	}

	private void displayAlbum(int number) {
		// TODO - Display all the informations about the album
		//
		//        Album Cover Image
		//        Enable next/previous album buttons, if there is more than one album

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
	}

	private List<Album> searchAlbumsFromArtist(String artistName) {
		try {
			String artistId = controller.getArtistId(artistName);
			List<String> albumIds = controller.getAlbumIds(artistId);
			List<Album> data = controller.getAlbumsData(albumIds);

			return data;
		} catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}

	@Override
	public void initialize(URL url, ResourceBundle rb) {

		// Setup Table View
		TableColumn<TrackData, Number> trackNumberColumn = new TableColumn("#");
		trackNumberColumn.setCellValueFactory((CellDataFeatures<TrackData, Number> p) -> {
			return p.getValue().getTrackNumber();
		});

		TableColumn trackTitleColumn = new TableColumn("Title");
		trackTitleColumn.setCellValueFactory(new PropertyValueFactory("trackTitle"));
		trackTitleColumn.setPrefWidth(250);

		TableColumn playColumn = new TableColumn("Preview");
		playColumn.setCellValueFactory(new PropertyValueFactory("trackPreviewUrl"));
		Callback<TableColumn<TrackData, String>, TableCell<TrackData, String>> cellFactory = new Callback<TableColumn<TrackData, String>, TableCell<TrackData, String>>() {
			@Override
			public TableCell<TrackData, String> call(TableColumn<TrackData, String> param) {
				final TableCell<TrackData, String> cell = new TableCell<TrackData, String>() {
					final Button playButton = new Button("Play");

					@Override
					public void updateItem(String item, boolean empty) {
						if (item != null && !item.isEmpty()) {
							playButton.setOnAction(event -> {
								playPauseTrackPreview(playButton, item);
							});

							setGraphic(playButton);
						} else {
							setGraphic(null);
						}

						setText(null);
					}
				};

				return cell;
			}
		};
		playColumn.setCellFactory(cellFactory);
		tracksTableView.getColumns().setAll(trackNumberColumn, trackTitleColumn, playColumn);

		// When slider is released, we must seek in the song
		trackSlider.setOnMouseReleased(new EventHandler() {
			@Override
			public void handle(Event event) {
				if (mediaPlayer != null) {
					mediaPlayer.seek(Duration.seconds(trackSlider.getValue()));
				}
			}
		});

		// Initialize GUI
		this.artistName = "Pink Floyd";
		this.albums = searchAlbumsFromArtist(artistName);
		displayAlbum(0);
	}
}
