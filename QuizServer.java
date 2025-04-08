import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizServer extends JFrame {
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JSpinner portSpinner;
    private JTextField filePathField;
    private JButton browseButton;
    
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private List<Question> questions = new ArrayList<>();
    private ExecutorService threadPool;
    
    public QuizServer() {
        setTitle("Quiz Server");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Main panels
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Control panel
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // File selection components
        gbc.gridx = 0;
        gbc.gridy = 0;
        controlPanel.add(new JLabel("Questions File:"), gbc);
        
        filePathField = new JTextField("questions.txt", 20);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        controlPanel.add(filePathField, gbc);
        
        browseButton = new JButton("Browse");
        gbc.gridx = 2;
        gbc.weightx = 0;
        controlPanel.add(browseButton, gbc);
        
        // Port configuration
        gbc.gridx = 0;
        gbc.gridy = 1;
        controlPanel.add(new JLabel("Port:"), gbc);
        
        portSpinner = new JSpinner(new SpinnerNumberModel(12345, 1024, 65535, 1));
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        controlPanel.add(portSpinner, gbc);
        
        // Start/Stop buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        controlPanel.add(buttonPanel, gbc);
        
        // Status label
        statusLabel = new JLabel("Server Stopped", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);
        gbc.gridx = 0;
        gbc.gridy = 3;
        controlPanel.add(statusLabel, gbc);
        
        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        // Add components to main panel
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        add(mainPanel);
        
        // Setup event listeners
        setupListeners();
        
        // Center on screen
        setLocationRelativeTo(null);
    }
    
    private void setupListeners() {
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                filePathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        
        startButton.addActionListener(e -> startServer());
        
        stopButton.addActionListener(e -> stopServer());
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isRunning) {
                    stopServer();
                }
            }
        });
    }
    
    private void startServer() {
        if (!loadQuestions()) {
            return;
        }
        
        int port = (Integer) portSpinner.getValue();
        
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            threadPool = Executors.newCachedThreadPool();
            
            // Update UI
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portSpinner.setEnabled(false);
            filePathField.setEnabled(false);
            browseButton.setEnabled(false);
            statusLabel.setText("Server Running on port " + port);
            statusLabel.setForeground(Color.GREEN);
            
            logMessage("Server started on port " + port);
            logMessage("Loaded " + questions.size() + " questions");
            
            // Start accepting client connections in a separate thread
            threadPool.execute(() -> {
                try {
                    while (isRunning) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            threadPool.execute(new ClientHandler(clientSocket, questions));
                            SwingUtilities.invokeLater(() -> 
                                logMessage("New client connected: " + clientSocket.getInetAddress().getHostAddress())
                            );
                        } catch (SocketException se) {
                            // Server socket closed
                            if (isRunning) {
                                SwingUtilities.invokeLater(() -> 
                                    logMessage("Error accepting client connection: " + se.getMessage())
                                );
                            }
                        } catch (IOException e) {
                            SwingUtilities.invokeLater(() -> 
                                logMessage("Error accepting client connection: " + e.getMessage())
                            );
                        }
                    }
                } finally {
                    closeServerSocket();
                }
            });
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to start server: " + e.getMessage(), 
                "Server Error", 
                JOptionPane.ERROR_MESSAGE);
            logMessage("Error: " + e.getMessage());
        }
    }
    
    private void stopServer() {
        isRunning = false;
        
        // Shutdown thread pool
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        
        // Close server socket
        closeServerSocket();
        
        // Update UI
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            portSpinner.setEnabled(true);
            filePathField.setEnabled(true);
            browseButton.setEnabled(true);
            statusLabel.setText("Server Stopped");
            statusLabel.setForeground(Color.RED);
            logMessage("Server stopped");
        });
    }
    
    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logMessage("Error closing server socket: " + e.getMessage());
            }
        }
    }
    
    private boolean loadQuestions() {
        String filePath = filePathField.getText().trim();
        File questionFile = new File(filePath);
        
        if (!questionFile.exists() || !questionFile.isFile()) {
            JOptionPane.showMessageDialog(this, 
                "Question file not found: " + filePath, 
                "File Error", 
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        questions.clear();
        
        try (FileInputStream fis = new FileInputStream(questionFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            
            String line;
            Question currentQuestion = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }
                
                // Question line starts with "Q:"
                if (line.startsWith("Q:")) {
                    if (currentQuestion != null && currentQuestion.isValid()) {
                        questions.add(currentQuestion);
                    }
                    currentQuestion = new Question();
                    currentQuestion.setQuestion(line.substring(2).trim());
                }
                // Option line starts with number and dot
                else if (line.matches("^\\d+\\..*") && currentQuestion != null) {
                    String option = line.substring(line.indexOf('.') + 1).trim();
                    currentQuestion.addOption(option);
                }
                // Answer line starts with "A:"
                else if (line.startsWith("A:") && currentQuestion != null) {
                    try {
                        int answer = Integer.parseInt(line.substring(2).trim());
                        currentQuestion.setCorrectAnswer(answer);
                    } catch (NumberFormatException e) {
                        logMessage("Invalid answer format: " + line);
                    }
                }
            }
            
            // Add the last question
            if (currentQuestion != null && currentQuestion.isValid()) {
                questions.add(currentQuestion);
            }
            
            if (questions.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "No valid questions found in the file.", 
                    "File Error", 
                    JOptionPane.WARNING_MESSAGE);
                return false;
            }
            
            return true;
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Error reading question file: " + e.getMessage(), 
                "File Error", 
                JOptionPane.ERROR_MESSAGE);
            logMessage("Error reading file: " + e.getMessage());
            return false;
        }
    }
    
    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new Date() + "] " + message + "\n");
            // Auto-scroll to bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            QuizServer server = new QuizServer();
            server.setVisible(true);
        });
    }
    
    // Inner class to handle client connections
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private List<Question> questions;
        private PrintWriter out;
        private BufferedReader in;
        private String clientAddress;
        
        public ClientHandler(Socket socket, List<Question> questions) {
            this.clientSocket = socket;
            this.questions = questions;
            this.clientAddress = socket.getInetAddress().getHostAddress();
        }
        
        @Override
        public void run() {
            try {
                // Set up I/O streams
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                
                // Send number of questions
                out.println("TOTAL:" + questions.size());
                
                int currentQuestion = 0;
                int score = 0;
                
                // Process each question
                while (currentQuestion < questions.size()) {
                    Question q = questions.get(currentQuestion);
                    
                    // Send question to client
                    out.println("QUESTION:" + q.getQuestion());
                    
                    // Send options
                    List<String> options = q.getOptions();
                    out.println("OPTIONS:" + options.size());
                    for (String option : options) {
                        out.println(option);
                    }
                    
                    // Wait for answer from client
                    String response = in.readLine();
                    if (response == null) {
                        break; // Client disconnected
                    }
                    
                    if (response.startsWith("ANSWER:")) {
                        try {
                            int answer = Integer.parseInt(response.substring(7));
                            boolean isCorrect = (answer == q.getCorrectAnswer());
                            
                            // Save the current question number before incrementing it
                            final int questionNumber = currentQuestion + 1;
                            
                            if (isCorrect) {
                                score++;
                                out.println("RESULT:CORRECT");
                                SwingUtilities.invokeLater(() -> 
                                    logMessage("Client " + clientAddress + " answered correctly to question " + questionNumber)
                                );
                            } else {
                                out.println("RESULT:INCORRECT:" + q.getCorrectAnswer());
                                SwingUtilities.invokeLater(() -> 
                                    logMessage("Client " + clientAddress + " answered incorrectly to question " + questionNumber)
                                );
                            }
                            
                            currentQuestion++;
                            
                            // Send current score
                            out.println("SCORE:" + score + "/" + currentQuestion);
                            
                        } catch (NumberFormatException e) {
                            out.println("ERROR:Invalid answer format");
                        }
                    }
                }
                
                // Quiz completed - capture final score values before using in lambda
                final int finalScore = score;
                final int totalQuestions = questions.size();
                
                // Send results to client
                out.println("FINISHED:Your final score is " + finalScore + " out of " + totalQuestions);
                
                // Log the results using the final variables
                SwingUtilities.invokeLater(() -> 
                    logMessage("Client " + clientAddress + " finished quiz with score " + finalScore + "/" + totalQuestions)
                );
                
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> 
                    logMessage("Error handling client " + clientAddress + ": " + e.getMessage())
                );
            } finally {
                try {
                    if (out != null) out.close();
                    if (in != null) in.close();
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> 
                        logMessage("Error closing client connection: " + e.getMessage())
                    );
                }
            }
        }
    }
}

// Question class to store quiz questions
class Question {
    private String question;
    private List<String> options = new ArrayList<>();
    private int correctAnswer;
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public List<String> getOptions() {
        return options;
    }
    
    public void addOption(String option) {
        options.add(option);
    }
    
    public int getCorrectAnswer() {
        return correctAnswer;
    }
    
    public void setCorrectAnswer(int correctAnswer) {
        this.correctAnswer = correctAnswer;
    }
    
    public boolean isValid() {
        return question != null && !question.isEmpty() && 
               !options.isEmpty() && 
               correctAnswer > 0 && correctAnswer <= options.size();
    }
}