package openjml;

import org.jmlspecs.openjml.API;

import java.io.IOException;

public class OpenJMLAPI extends API {
    private static OpenJMLAPI instance;

    public static OpenJMLAPI getInstance() {
        if (instance == null) {
            try {
                instance = new OpenJMLAPI();
            } catch (IOException e) {
                throw new RuntimeException("Unable to load OpenJML API", e);
            }
        }

        return instance;
    }

    private OpenJMLAPI() throws IOException {
        super();
    }
}
