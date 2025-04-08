import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

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
        
        optionsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        optionsGroup = new ButtonGroup();
        JScrollPane optionsScrollPane = new JScrollPane(optionsPanel);
        questionPanel.add(optionsScrollPane, BorderLayout.CENTER);
        
        // Submit panel
        JPanel submitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        submitButton = new JButton("Submit Answer");
        submitButton.setEnabled(false);
        submitPanel.add(submitButton);
        questionPanel.add(submitPanel, BorderLayout.SOUTH);
        
        // Score panel (South)
        scorePanel = new JPanel(new BorderLayout(5, 5));
        scorePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        scoreLabel = new JLabel("Score: 0/0", SwingConstants.CENTER);
        scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        scorePanel.add(scoreLabel, BorderLayout.NORTH);
        
        feedbackLabel = new JLabel("", SwingConstants.CENTER);
        feedbackLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        scorePanel.add(feedbackLabel, BorderLayout.CENTER);
        
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
        });
    }
    
    private void startQuiz() {
        try {
            String line;
            
            // Get total questions from server
            while ((line = in.readLine()) != null && isConnected) {
                if (line.startsWith("TOTAL:")) {
                    totalQuestions = Integer.parseInt(line.substring(6));
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setMaximum(totalQuestions);
                        progressBar.setValue(0);
                        progressBar.setString("0/" + totalQuestions);
                    });
                    break;
                }
            }
            
            // Process questions
            currentQuestionIndex = 0;
            String currentQuestion = "";
            List<String> options = new ArrayList<>();
            
            while ((line = in.readLine()) != null && isConnected) {
                if (line.startsWith("QUESTION:")) {
                    currentQuestion = line.substring(9);
                    options.clear();
                    
                    final String questionText = currentQuestion;
                    SwingUtilities.invokeLater(() -> {
                        questionLabel.setText("<html><div style='text-align: center;'>" + 
                                              "Question " + (currentQuestionIndex + 1) + "/" + totalQuestions + 
                                              ":<br>" + questionText + "</div></html>");
                    });
                    
                } else if (line.startsWith("OPTIONS:")) {
                    int numOptions = Integer.parseInt(line.substring(8));
                    
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
                    });
                    
                } else if (line.startsWith("RESULT:")) {
                    String result = line.substring(7);
                    if (result.startsWith("CORRECT")) {
                        SwingUtilities.invokeLater(() -> {
                            feedbackLabel.setText("Correct answer!");
                            feedbackLabel.setForeground(new Color(0, 150, 0));
                        });
                    } else if (result.startsWith("INCORRECT")) {
                        int correctAnswer = Integer.parseInt(result.split(":")[1]);
                        SwingUtilities.invokeLater(() -> {
                            feedbackLabel.setText("Incorrect! The correct answer was: " + correctAnswer);
                            feedbackLabel.setForeground(Color.RED);
                        });
                    }
                    
                } else if (line.startsWith("SCORE:")) {
                    String scoreInfo = line.substring(6);
                    final String scoreText = "Score: " + scoreInfo;
                    
                    SwingUtilities.invokeLater(() -> {
                        scoreLabel.setText(scoreText);
                        currentQuestionIndex++;
                        progressBar.setValue(currentQuestionIndex);
                        progressBar.setString(currentQuestionIndex + "/" + totalQuestions);
                    });
                    
                } else if (line.startsWith("FINISHED:")) {
                    final String message = line.substring(9);
                    SwingUtilities.invokeLater(() -> {
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
                    
                    // No need to disconnect as the server will keep the connection open
                    // to allow the client to see the final results
                    
                } else if (line.startsWith("ERROR:")) {
                    final String errorMsg = line.substring(6);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "Server error: " + errorMsg,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    });
                }
            }
            
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Lost connection to server: " + e.getMessage(),
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                    disconnectFromServer();
                });
            }
        }
    }
    
    private void displayOptions(List<String> options) {
        clearOptions();
        
        for (int i = 0; i < options.size(); i++) {
            final int optionIndex = i + 1;
            JRadioButton radioButton = new JRadioButton(optionIndex + ". " + options.get(i));
            radioButton.setActionCommand(String.valueOf(optionIndex));
            optionsGroup.add(radioButton);
            optionsPanel.add(radioButton);
            optionButtons.add(radioButton);
        }
        
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
        ButtonModel selectedButton = optionsGroup.getSelection();
        
        if (selectedButton == null) {
            JOptionPane.showMessageDialog(this,
                "Please select an answer before submitting.",
                "No Answer Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String answer = selectedButton.getActionCommand();
        out.println("ANSWER:" + answer);
        submitButton.setEnabled(false);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            QuizClient client = new QuizClient();
            client.setVisible(true);
        });
    }
}