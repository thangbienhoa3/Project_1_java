package org.example.gui.service;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Handles the face login flow by delegating the heavy lifting to the Python process service
 * and interpreting the result as a recognized username.
 */
public class FaceLoginService {

    private final PythonProcessService pythonProcessService;

    public FaceLoginService(PythonProcessService pythonProcessService) {
        this.pythonProcessService = Objects.requireNonNull(pythonProcessService, "pythonProcessService");
    }

    public void loginByFace(Consumer<LoginResult> callback) {
        pythonProcessService.executeAsync(
                List.of("python3", "face_app.py", "login"),
                result -> {
                    LoginResult loginResult = toLoginResult(result);
                    if (callback != null) {
                        callback.accept(loginResult);
                    }
                }
        );
    }

    private LoginResult toLoginResult(PythonProcessService.PythonResult result) {
        if (result.exitCode() != 0) {
            String message = result.output() == null || result.output().isBlank()
                    ? "Python exited with code " + result.exitCode()
                    : result.output();
            return LoginResult.failure(message);
        }

        String output = result.output();
        if (output == null || output.isBlank()) {
            return LoginResult.failure("Face not recognized or face has not been registered");
        }

        String username = output.strip();
        return LoginResult.success(username);
    }

    public record LoginResult(boolean success, String username, String message) {
        public static LoginResult success(String username) {
            return new LoginResult(true, username, null);
        }

        public static LoginResult failure(String message) {
            return new LoginResult(false, null, message);
        }
    }
}