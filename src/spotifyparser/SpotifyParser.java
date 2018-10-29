package spotifyparser;

import java.io.IOException;
import java.util.Scanner;

public class SpotifyParser {

	public static void main(String[] args) throws IOException {
		SpotifyController controller = new SpotifyController();
		controller.authenticate();
		
		Scanner sc = new Scanner(System.in);
		while (true) {
			System.out.print("URL endpoint: ");
			String endpoint = sc.nextLine();
			
			System.out.print("Parameters: ");
			String params = sc.nextLine();
			
			System.err.println(controller.sendRequest(endpoint, params));
		}

	}

}
