import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import javax.swing.*;

public class QuizClient extends JFrame {
    private JTextField serverAddressField;
    private JSpinner portSpinner;
    private JButton connectButton;
    private JButton disconnectButton;
    private JLabel statusLabel;
    private JPanel questionPanel;
    private JLabel questionLabel;
    private JPanel optionsPanel;
    private ButtonGroup optionsGroup;
    private JButton submitButton;
    private JPanel scorePanel;
    private JLabel scoreLabel;
    private JLabel feedbackLabel;
    private JProgressBar progressBar;
    // Timer components
    private JProgressBar timerBar;
    private JLabel timerLabel;
    private Timer questionTimer;
    private int timeLimit = 30; // Default, will be updated from server
    private int timeRemaining;
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private List<JRadioButton> optionButtons = new ArrayList<>();
    private boolean isConnected = false;
    private int totalQuestions = 0;
    private int currentQuestionIndex = 0;
    
    public QuizClient() {
        setTitle("Quiz Client");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Connection panel (North)
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Server address
        gbc.gridx = 0;
        gbc.gridy = 0;
        connectionPanel.add(new JLabel("Server Address:"), gbc);
        
        serverAddressField = new JTextField("localhost", 15);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        connectionPanel.add(serverAddressField, gbc);
        
        // Port
        gbc.gridx = 2;
        gbc.weightx = 0;
        connectionPanel.add(new JLabel("Port:"), gbc);
        
        portSpinner = new JSpinner(new SpinnerNumberModel(12345, 1024, 65535, 1));
        gbc.gridx = 3;
        connectionPanel.add(portSpinner, gbc);
        
        // Connect/Disconnect buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        connectButton = new JButton("Connect");
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        buttonPanel.add(connectButton);
        buttonPanel.add(disconnectButton);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        connectionPanel.add(buttonPanel, gbc);
        
        // Status label
        statusLabel = new JLabel("Not connected", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);
        gbc.gridy = 2;
        connectionPanel.add(statusLabel, gbc);
        
        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0/0");
        gbc.gridy = 3;
        connectionPanel.add(progressBar, gbc);
        
        // Question panel (Center)
        questionPanel = new JPanel(new BorderLayout(10, 10));
        questionPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        questionLabel = new JLabel("Connect to a quiz server to start.", SwingConstants.CENTER);
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        questionPanel.add(questionLabel, BorderLayout.NORTH);
        
        // Add timer panel below question
        JPanel timerPanel = new JPanel(new BorderLayout(5, 0));
        timerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        timerLabel = new JLabel("Time: 0s", SwingConstants.LEFT);
        timerBar = new JProgressBar(0, timeLimit);
        timerBar.setStringPainted(true);
        timerBar.setString("0s");
        timerPanel.add(timerLabel, BorderLayout.WEST);
        timerPanel.add(timerBar, BorderLayout.CENTER);
        questionPanel.add(timerPanel, BorderLayout.CENTER);
        
        optionsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        optionsGroup = new ButtonGroup();
        JScrollPane optionsScrollPane = new JScrollPane(optionsPanel);
        questionPanel.add(optionsScrollPane, BorderLayout.SOUTH);
        
        // Submit panel
        JPanel submitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        submitButton = new JButton("Submit Answer");
        submitButton.setEnabled(false);
        submitPanel.add(submitButton);
        
        // Score panel (South)
        scorePanel = new JPanel(new BorderLayout(5, 5));
        scorePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        scoreLabel = new JLabel("Score: 0/0", SwingConstants.CENTER);
        scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        scorePanel.add(scoreLabel, BorderLayout.NORTH);
        
        feedbackLabel = new JLabel("", SwingConstants.CENTER);
        feedbackLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        scorePanel.add(feedbackLabel, BorderLayout.CENTER);
        scorePanel.add(submitPanel, BorderLayout.SOUTH);
        
        // Add panels to main panel
        mainPanel.add(connectionPanel, BorderLayout.NORTH);
        mainPanel.add(questionPanel, BorderLayout.CENTER);
        mainPanel.add(scorePanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Setup event listeners
        setupListeners();
        
        // Center on screen
        setLocationRelativeTo(null);
    }
    
    private void setupListeners() {
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer());
        submitButton.addActionListener(e -> submitAnswer());
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isConnected) {
                    disconnectFromServer();
                }
            }
        });
    }
    
    private void connectToServer() {
        String serverAddress = serverAddressField.getText().trim();
        int port = (Integer) portSpinner.getValue();
        
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            isConnected = true;
            
            // Update UI
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            serverAddressField.setEnabled(false);
            portSpinner.setEnabled(false);
            statusLabel.setText("Connected to " + serverAddress + ":" + port);
            statusLabel.setForeground(Color.GREEN);
            
            // Start quiz in a separate thread
            new Thread(this::startQuiz).start();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to connect to server: " + e.getMessage(), 
                "Connection Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void disconnectFromServer() {
        isConnected = false;
        
        // Stop any active timer
        stopQuestionTimer();
        
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
        
        // Reset UI
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            serverAddressField.setEnabled(true);
            portSpinner.setEnabled(true);
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(Color.RED);
            submitButton.setEnabled(false);
            questionLabel.setText("Connect to a quiz server to start.");
            clearOptions();
            scoreLabel.setText("Score: 0/0");
            feedbackLabel.setText("");
            progressBar.setValue(0);
            progressBar.setString("0/0");
            timerBar.setValue(0);
            timerBar.setString("0s");
            timerLabel.setText("Time: 0s");
        });
    }
    
    // Start the question timer
    private void startQuestionTimer() {
        // Cancel any existing timer
        stopQuestionTimer();
        
        timeRemaining = timeLimit;
        timerBar.setMaximum(timeLimit);
        timerBar.setValue(timeLimit);
        timerBar.setString(timeLimit + "s");
        timerBar.setForeground(Color.GREEN);
        timerLabel.setText("Time: " + timeLimit + "s");
        
        questionTimer = new Timer();
        questionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (timeRemaining > 0) {
                    timeRemaining--;
                    
                    SwingUtilities.invokeLater(() -> {
                        timerBar.setValue(timeRemaining);
                        timerBar.setString(timeRemaining + "s");
                        timerLabel.setText("Time: " + timeRemaining + "s");
                        
                        // Change color based on time remaining
                        if (timeRemaining < timeLimit * 0.25) {
                            timerBar.setForeground(Color.RED);
                        } else if (timeRemaining < timeLimit * 0.5) {
                            timerBar.setForeground(Color.ORANGE);
                        }
                    });
                } else {
                    // Time's up - cancel the timer
                    this.cancel();
                }
            }
        }, 0, 1000);
    }
    
    // Stop the question timer
    private void stopQuestionTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
    }
    
    private void startQuiz() {
        try {
            String line;
            
            // Process messages from server
            while ((line = in.readLine()) != null && isConnected) {
                // Get total questions from server
                if (line.startsWith("TOTAL:")) {
                    totalQuestions = Integer.parseInt(line.substring(6));
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setMaximum(totalQuestions);
                        progressBar.setValue(0);
                        progressBar.setString("0/" + totalQuestions);
                    });
                    continue;
                }
                
                // Get time limit from server
                if (line.startsWith("TIMELIMIT:")) {
                    timeLimit = Integer.parseInt(line.substring(10));
                    SwingUtilities.invokeLater(() -> {
                        timerBar.setMaximum(timeLimit);
                        timerBar.setValue(0);
                        timerBar.setString("0s");
                        timerLabel.setText("Time: 0s");
                    });
                    continue;
                }
                
                // Process question
                if (line.startsWith("QUESTION:")) {
                    String currentQuestion = line.substring(9);
                    
                    final String questionText = currentQuestion;
                    SwingUtilities.invokeLater(() -> {
                        questionLabel.setText("<html><div style='text-align: center;'>" + 
                                             "Question " + (currentQuestionIndex + 1) + "/" + totalQuestions + 
                                             ":<br>" + questionText + "</div></html>");
                        feedbackLabel.setText("");
                    });
                    continue;
                }
                
                // Process options
                if (line.startsWith("OPTIONS:")) {
                    int numOptions = Integer.parseInt(line.substring(8));
                    List<String> options = new ArrayList<>();
                    
                    for (int i = 0; i < numOptions && isConnected; i++) {
                        String option = in.readLine();
                        if (option != null) {
                            options.add(option);
                        }
                    }
                    
                    final List<String> finalOptions = new ArrayList<>(options);
                    SwingUtilities.invokeLater(() -> {
                        displayOptions(finalOptions);
                        submitButton.setEnabled(true);
                        startQuestionTimer(); // Start timer when options are displayed
                    });
                    continue;
                }
                
                // Handle time expired notification
                if (line.equals("TIME_EXPIRED")) {
                    SwingUtilities.invokeLater(() -> {
                        stopQuestionTimer();
                        JOptionPane.showMessageDialog(this,
                            "Time expired! The question will be marked as incorrect.",
                            "Time's Up",
                            JOptionPane.WARNING_MESSAGE);
                        // Auto-submit with current selection (or no selection)
                        submitAnswer();
                    });
                    continue;
                }
                
                // Process result from server - handle both original and new formats
                if (line.startsWith("RESULT:")) {
                    String result = line.substring(7);
                    if (result.startsWith("CORRECT")) {
                        SwingUtilities.invokeLater(() -> {
                            stopQuestionTimer();
                            feedbackLabel.setText("Correct answer!");
                            feedbackLabel.setForeground(new Color(0, 150, 0));
                            submitButton.setEnabled(false);
                        });
                    } else if (result.startsWith("INCORRECT:")) {
                        int correctAnswer = Integer.parseInt(result.split(":")[1]);
                        SwingUtilities.invokeLater(() -> {
                            stopQuestionTimer();
                            feedbackLabel.setText("Incorrect! The correct answer was: " + correctAnswer);
                            feedbackLabel.setForeground(Color.RED);
                            submitButton.setEnabled(false);
                        });
                    } else if (result.startsWith("TIMEOUT:")) {
                        int correctAnswer = Integer.parseInt(result.split(":")[1]);
                        SwingUtilities.invokeLater(() -> {
                            stopQuestionTimer();
                            feedbackLabel.setText("Time expired! The correct answer was: " + correctAnswer);
                            feedbackLabel.setForeground(Color.RED);
                            submitButton.setEnabled(false);
                        });
                    }
                    continue;
                }
                
                // Process score update
                if (line.startsWith("SCORE:")) {
                    String scoreInfo = line.substring(6);
                    final String scoreText = "Score: " + scoreInfo;
                    
                    SwingUtilities.invokeLater(() -> {
                        scoreLabel.setText(scoreText);
                        currentQuestionIndex++;
                        progressBar.setValue(currentQuestionIndex);
                        progressBar.setString(currentQuestionIndex + "/" + totalQuestions);
                        
                        // Reset timer display
                        timerBar.setValue(0);
                        timerBar.setString("0s");
                        timerLabel.setText("Time: 0s");
                    });
                    continue;
                }
                
                // Handle quiz completion
                if (line.startsWith("FINISHED:")) {
                    final String message = line.substring(9);
                    SwingUtilities.invokeLater(() -> {
                        stopQuestionTimer();
                        questionLabel.setText("Quiz Completed!");
                        clearOptions();
                        submitButton.setEnabled(false);
                        feedbackLabel.setText(message);
                        feedbackLabel.setForeground(Color.BLUE);
                        
                        // Show dialog with final score
                        JOptionPane.showMessageDialog(this,
                            message,
                            "Quiz Completed",
                            JOptionPane.INFORMATION_MESSAGE);
                    });
                    continue;
                }
                
                // Server error
                if (line.startsWith("ERROR:")) {
                    final String errorMsg = line.substring(6);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, 
                            "Server error: " + errorMsg, 
                            "Error", 
                            JOptionPane.ERROR_MESSAGE);
                    });
                    continue;
                }
            }
            
            // If we're here and still connected, server probably disconnected
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    disconnectFromServer();
                    JOptionPane.showMessageDialog(this, 
                        "Lost connection to the server", 
                        "Connection Lost", 
                        JOptionPane.WARNING_MESSAGE);
                });
            }
            
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    disconnectFromServer();
                    JOptionPane.showMessageDialog(this, 
                        "Connection error: " + e.getMessage(), 
                        "Connection Error", 
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }
    
    private void displayOptions(List<String> options) {
        // Clear previous options
        clearOptions();
        
        // Add new options
        for (int i = 0; i < options.size(); i++) {
            final int optionIndex = i + 1;
            JRadioButton radioButton = new JRadioButton(optionIndex + ". " + options.get(i));
            radioButton.setActionCommand(String.valueOf(optionIndex));
            optionsGroup.add(radioButton);
            optionsPanel.add(radioButton);
            optionButtons.add(radioButton);
        }
        
        // If there are options, select the first one by default
        if (!optionButtons.isEmpty()) {
            optionButtons.get(0).setSelected(true);
        }
        
        // Revalidate and repaint
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }
    
    private void clearOptions() {
        optionsGroup = new ButtonGroup();
        optionsPanel.removeAll();
        optionButtons.clear();
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }
    
    private void submitAnswer() {
        if (!isConnected) return;
        
        ButtonModel selectedButton = optionsGroup.getSelection();
        
        if (selectedButton == null) {
            // If no answer is selected, send -1 to indicate no selection
            out.println("ANSWER:-1");
        } else {
            String answer = selectedButton.getActionCommand();
            out.println("ANSWER:" + answer);
        }
        
        // Disable submit button until next question
        submitButton.setEnabled(false);
    }
    
    public static void main(String[] args) {
        // Set look and feel to the system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | 
                 IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
        
        // Create and show GUI
        SwingUtilities.invokeLater(() -> {
            QuizClient client = new QuizClient();
            client.setVisible(true);
        });
    }
}
