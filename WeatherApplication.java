import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;


public class WeatherApplication {
    private static final String API_KEY = "58d78623c7ec3fd71aeeee00866fa790";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/myweather";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "jithu123";


    private JFrame frame;
    private JTextField cityField;
    private JTextArea weatherDisplay;
    private JLabel cloudLabel;
    private Timer hourlyTimer;
    private String loggedInUsername; // New field to store the logged-in username

    // In-memory user credentials storage (Replace this with a database in a real-world scenario)
    HashMap<String, String> userCredentials = new HashMap<>();

    public WeatherApplication() {
        frame = new JFrame("World Weather Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(null);

        cityField = new JTextField(15);
        JButton fetchButton = new JButton("Fetch Weather");
        weatherDisplay = new JTextArea(10, 30);
        weatherDisplay.setEditable(false);
        cloudLabel = new JLabel();

        cityField.setBounds(20, 20, 150, 20);
        fetchButton.setBounds(180, 20, 150, 20);
        weatherDisplay.setBounds(20, 50, 350, 200);
        cloudLabel.setBounds(400, 50, 150, 150);

        ImageIcon initialCloud = new ImageIcon("initial_cloud_image.png");
        cloudLabel.setIcon(initialCloud);

        frame.add(cityField);
        frame.add(fetchButton);
        frame.add(weatherDisplay);
        frame.add(cloudLabel);

        fetchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String city = cityField.getText();
                String weatherInfo = fetchWeatherData(city);
                weatherDisplay.setText(weatherInfo);
                updateCloudAnimation(weatherInfo);
                saveWeatherDataToDatabase(city, weatherInfo);
                compareAndRecommendDestination(weatherInfo);

                int delay = 0; // initial delay
                int interval = 3600000; // 1 hour in milliseconds
                hourlyTimer = new Timer();
                hourlyTimer.scheduleAtFixedRate(new WeatherUpdateTask(), delay, interval);
            }
        });
    }

    private class WeatherUpdateTask extends TimerTask {
        @Override
        public void run() {
            SwingUtilities.invokeLater(() -> {
                String city = cityField.getText();
                String updatedWeatherInfo = fetchWeatherData(city);
                weatherDisplay.setText(updatedWeatherInfo);
                updateCloudAnimation(updatedWeatherInfo);
                saveWeatherDataToDatabase(city, updatedWeatherInfo);
            });
        }
    }

    private void compareAndRecommendDestination(String weatherInfo) {
        String temperatureString = weatherInfo.split("Temperature: ")[1].split(" °C")[0];
        double temperature = Double.parseDouble(temperatureString);

        String recommendation;
        if (temperature < 10) {
            recommendation = "It's cold! Consider visiting a place with warmer weather.";
            String[] recommendedCities = {"Barcelona", "Sydney", "Miami"};
            displayRecommendedCities(recommendedCities);
        } else if (temperature >= 10 && temperature < 25) {
            recommendation = "The weather is moderate. Enjoy your trip!";
        } else {
            recommendation = "It's hot! Consider visiting a place with cooler weather.";
            String[] recommendedCities = {"Reykjavik", "Oslo", "Vancouver"};
            displayRecommendedCities(recommendedCities);
        }

        JOptionPane.showMessageDialog(frame, recommendation, "Destination Recommendation", JOptionPane.INFORMATION_MESSAGE);
    }

    private void displayRecommendedCities(String[] cities) {
        String cityList = String.join("\n", cities);
        JOptionPane.showMessageDialog(frame, "Recommended cities:\n" + cityList, "Recommended Cities", JOptionPane.INFORMATION_MESSAGE);
    }

    private String parseTemperature1(String jsonData) {
        if (jsonData.contains("\"temp\":")) {
            String temperatureKelvin = jsonData.split("\"temp\":")[1].split(",")[0];
            double temperatureKelvinDouble = Double.parseDouble(temperatureKelvin);
            double temperatureCelsius = temperatureKelvinDouble - 273.15;
            return String.format("%.2f", temperatureCelsius);
        } else {
            return "N/A";
        }
    }

    private String fetchWeatherData(String city) {
        try {
            URL url = new URL("https://api.openweathermap.org/data/2.5/weather?q=" + city + "&appid=" + API_KEY);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();
            connection.disconnect();
            String jsonData = response.toString();
            String temperature = parseTemperature1(jsonData);
            String humidity = parseHumidity(jsonData);
            String windSpeed = parseWindSpeed(jsonData);

            return "Temperature: " + temperature + " °C\nHumidity: " + humidity + "%\nWind Speed: " + windSpeed + " km/h";
        } catch (Exception e) {
            return "Failed to fetch weather data. Please check your city and API key.";
        }
    }

    private void saveWeatherDataToDatabase(String city, String weatherInfo) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Truncate weatherInfo to fit into the column
            int maxLength = 255; // Set the maximum length you want to store
            if (weatherInfo.length() > maxLength) {
                weatherInfo = weatherInfo.substring(0, maxLength);
            }

            String insertQuery = "INSERT INTO weather_data (city, weather_info) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, city);
                preparedStatement.setString(2, weatherInfo);
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void updateCloudAnimation(String weatherInfo) {
        String temperatureString = weatherInfo.split("Temperature: ")[1].split(" °C")[0];
        double temperature = Double.parseDouble(temperatureString);

        if (temperature < 10) {
            ImageIcon coldCloud = new ImageIcon("cold_cloud_image.png");
            cloudLabel.setIcon(coldCloud);
        } else if (temperature >= 10 && temperature < 25) {
            ImageIcon moderateCloud = new ImageIcon("moderate_cloud_image.png");
            cloudLabel.setIcon(moderateCloud);
        } else {
            ImageIcon hotCloud = new ImageIcon("hot_cloud_image.png");
            cloudLabel.setIcon(hotCloud);
        }
    }

    private String parseHumidity(String jsonData) {
        return jsonData.contains("\"humidity\":") ? jsonData.split("\"humidity\":")[1].split(",")[0] : "N/A";
    }

    private String parseWindSpeed(String jsonData) {
        return jsonData.contains("\"speed\":") ? jsonData.split("\"speed\":")[1].split(",")[0] : "N/A";
    }

    public void display() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
        });
    }

    public String getLoggedInUsername() {
        return loggedInUsername;
    }

    public void setLoggedInUsername(String loggedInUsername) {
        this.loggedInUsername = loggedInUsername;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WeatherApplication weatherApp = new WeatherApplication();
            weatherApp.display();
        });
    }
}

class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private WeatherApplication weatherApp;

    public LoginFrame(WeatherApplication weatherApp) {
        this.weatherApp = weatherApp;

        setTitle("Login");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(3, 2));

        JLabel usernameLabel = new JLabel("Username:");
        JLabel passwordLabel = new JLabel("Password:");

        usernameField = new JTextField();
        passwordField = new JPasswordField();

        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register");

        add(usernameLabel);
        add(usernameField);
        add(passwordLabel);
        add(passwordField);
        add(loginButton);
        add(registerButton);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String password = String.valueOf(passwordField.getPassword());

                if (validateLogin(username, password)) {
                    weatherApp.setLoggedInUsername(username);
                    dispose();
                    weatherApp.display();
                } else {
                    JOptionPane.showMessageDialog(LoginFrame.this, "Invalid username or password", "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showRegisterFrame();
            }
        });
    }

    private void showRegisterFrame() {
        RegisterFrame registerFrame = new RegisterFrame(weatherApp);
        registerFrame.setVisible(true);
    }

    private boolean validateLogin(String username, String password) {
        // Replace this with your authentication logic (e.g., database lookup)
        String storedPassword = weatherApp.userCredentials.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }
}

class RegisterFrame extends JFrame {
    private JTextField newUsernameField;
    private JPasswordField newPasswordField;
    private WeatherApplication weatherApp;

    public RegisterFrame(WeatherApplication weatherApp) {
        this.weatherApp = weatherApp;

        setTitle("Register");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(3, 2));

        JLabel newUsernameLabel = new JLabel("New Username:");
        JLabel newPasswordLabel = new JLabel("New Password:");

        newUsernameField = new JTextField();
        newPasswordField = new JPasswordField();

        JButton registerButton = new JButton("Register");

        add(newUsernameLabel);
        add(newUsernameField);
        add(newPasswordLabel);
        add(newPasswordField);
        add(registerButton);

        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String newUsername = newUsernameField.getText();
                char[] newPasswordChars = newPasswordField.getPassword();
                String newPassword = new String(newPasswordChars);

                // Store the new user credentials (replace this with actual database storage)
                weatherApp.userCredentials.put(newUsername, newPassword);

                JOptionPane.showMessageDialog(RegisterFrame.this, "Registration successful", "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
                weatherApp.setLoggedInUsername(newUsername);
                weatherApp.display();
            }
        });
    }
}
