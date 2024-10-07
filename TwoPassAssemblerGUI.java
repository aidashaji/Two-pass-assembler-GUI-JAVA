import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.HashMap;

public class TwoPassAssemblerGUI extends JFrame {

    private JTextArea outputArea;
    private File assemblyFile, optabFile;

    // Data structures for Pass 1 and Pass 2
    private HashMap<String, Integer> symtab = new HashMap<>();
    private HashMap<String, String> optab = new HashMap<>();
    private StringBuilder intermediateFile = new StringBuilder();
    private StringBuilder machineCode = new StringBuilder();
    private int locationCounter = 0;  // Keep track of the location counter as a hexadecimal value.

    public TwoPassAssemblerGUI() {
        setTitle("Two-Pass Assembler");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create components
        JButton loadAssemblyButton = new JButton("Load Assembly Code");
        JButton loadOptabButton = new JButton("Load OPTAB");
        JButton assembleButton = new JButton("Assemble");
        outputArea = new JTextArea(15, 40);
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(loadAssemblyButton);
        buttonPanel.add(loadOptabButton);
        buttonPanel.add(assembleButton);

        add(buttonPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Add action listeners
        loadAssemblyButton.addActionListener(new LoadAssemblyAction());
        loadOptabButton.addActionListener(new LoadOptabAction());
        assembleButton.addActionListener(new AssembleAction());
    }

    // Load Assembly Code
    private class LoadAssemblyAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                assemblyFile = fileChooser.getSelectedFile();
                outputArea.append("Loaded Assembly Code: " + assemblyFile.getName() + "\n");
            }
        }
    }

    // Load OPTAB
    private class LoadOptabAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                optabFile = fileChooser.getSelectedFile();
                loadOptab(optabFile);
                outputArea.append("Loaded OPTAB: " + optabFile.getName() + "\n");
            }
        }
    }

    // Pass 1 and Pass 2 Combined Action
    private class AssembleAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (assemblyFile != null && !optab.isEmpty()) {
                executePass1();
                executePass2();
                displayOutput(); // Show the output in the interface instead of writing to a file
                outputArea.append("Assembling Complete!\n");
            } else {
                outputArea.append("Please load both Assembly Code and OPTAB first.\n");
            }
        }
    }

    // Load OPTAB from file
    private void loadOptab(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length == 2) {
                    optab.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            outputArea.append("Error loading OPTAB.\n");
        }
    }

    // Execute Pass 1: Generate Intermediate File and SYMTAB
    private void executePass1() {
        try (BufferedReader reader = new BufferedReader(new FileReader(assemblyFile))) {
            String line;
            locationCounter = 0; // Initialize location counter

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");

                // If the line is empty or a comment, skip it
                if (line.trim().isEmpty() || line.startsWith(".")) {
                    continue;
                }

                String label = parts.length > 1 && !optab.containsKey(parts[0]) ? parts[0] : "";
                String opcode = parts.length > 1 ? parts[parts.length > 2 ? 1 : 0] : "";
                String operand = parts.length > 2 ? parts[2] : ""; // Handle case where there's no operand

                // Check for START directive to set the starting address (hexadecimal)
                if (opcode.equals("START")) {
                    locationCounter = Integer.parseInt(operand, 16); // Parse the starting address in hex
                    intermediateFile.append(String.format("%04X", locationCounter)).append("\t").append(opcode).append("\t").append(operand).append("\n");
                    continue;
                }

                if (!label.isEmpty() &&  !label.equals("-") && !opcode.equals("END")) {
                    symtab.put(label, locationCounter);
                }

                intermediateFile.append(String.format("%04X", locationCounter)).append("\t");
                if (!label.isEmpty()) {
                    intermediateFile.append(label).append("\t");
                }
                intermediateFile.append(opcode).append("\t").append(operand).append("\n");

                // Update location counter based on opcode
                updateLocationCounter(opcode, operand);
            }
        } catch (IOException e) {
            outputArea.append("Error in Pass 1.\n");
        } catch (NumberFormatException e) {
            outputArea.append("Error: Invalid number format in operand.\n");
        }
    }

    // Update the location counter based on the opcode and operand
    private void updateLocationCounter(String opcode, String operand) {
        if (optab.containsKey(opcode)) {
            locationCounter += 3; // Increment by 3 for instructions
        } else if (opcode.equals("WORD")) {
            locationCounter += 3; // 3 bytes for WORD
        } else if (opcode.equals("RESW")) {
            locationCounter += 3 * Integer.parseInt(operand); // Multiply by number of words
        } else if (opcode.equals("RESB")) {
            locationCounter += Integer.parseInt(operand); // Reserve bytes
        } else if (opcode.equals("BYTE")) {
            if (operand.startsWith("C'") && operand.endsWith("'")) {
                int length = operand.length() - 3; // Subtract for C' and ending '
                locationCounter += length;
            } else if (operand.startsWith("X'") && operand.endsWith("'")) {
                int length = (operand.length() - 3) / 2; // Each hex pair is one byte
                locationCounter += length;
            }
        } else if (opcode.equals("END")) {
            intermediateFile.append("\t").append(opcode).append("\t").append(operand).append("\n");
        } else {
            outputArea.append("Unknown opcode or directive: " + opcode + "\n");
        }
    }

    // Execute Pass 2: Generate Machine Code
 // Execute Pass 2: Generate Machine Code with Intermediate File and Object Code
private void executePass2() {
    String[] intermediateLines = intermediateFile.toString().split("\n");
    StringBuilder objCodeOutput = new StringBuilder(); // Object code output
    StringBuilder pass2Output = new StringBuilder(); // For intermediate file with machine code
    String currentTextRecord = "";
    int currentTextStartAddress = -1;
    int currentTextLength = 0;
    String programName = "PROGRAM"; // Set a default program name; can be changed as needed

    // Extract the starting address from the first line of the intermediate file
    String firstLine = intermediateLines[0];
    String[] firstParts = firstLine.split("\\s+");
    int startingAddress = Integer.parseInt(firstParts[2], 16); // Get starting address from START directive
    int endingAddress = startingAddress; // Initialize ending address

    for (String line : intermediateLines) {
        String[] parts = line.split("\\s+");
        String address = parts[0];
        String label = parts.length > 1 ? parts[1] : "-";
        String opcode = parts.length > 2 ? parts[2] : "";
        String operand = parts.length > 3 ? parts[3] : "";

        String objCode = ""; // Initialize object code for this line

        // Generate object code based on the opcode
        if (opcode.equals("WORD")) {
            objCode = String.format("%06X", Integer.parseInt(operand.trim()));
        } else if (opcode.equals("BYTE")) {
            if (operand.startsWith("C'") && operand.endsWith("'")) {
                String strContent = operand.substring(2, operand.length() - 1);
                StringBuilder hexContent = new StringBuilder();
                for (char ch : strContent.toCharArray()) {
                    hexContent.append(String.format("%02X", (int) ch)); // Convert each character to hex
                }
                objCode = hexContent.toString();
            } else if (operand.startsWith("X'") && operand.endsWith("'")) {
                objCode = operand.substring(2, operand.length() - 1).toUpperCase();
            }
        } else if (optab.containsKey(opcode)) {
            objCode = optab.get(opcode);
            if (!operand.isEmpty() && symtab.containsKey(operand)) {
                String addressValue = String.format("%04X", symtab.get(operand));
                objCode += addressValue;
            }
        } else if (opcode.equals("RESW") || opcode.equals("RESB") || opcode.equals("START") || opcode.equals("END")) {
            objCode = ""; // Skip for these directives
        } else {
            outputArea.append("Unknown opcode: " + opcode + "\n");
        }

        // Update pass2 output
        pass2Output.append(String.format("%s\t%s\t%s\t%s\t%s\n", address, label, opcode, operand, objCode));

        // Build text records for object code
        if (!objCode.isEmpty()) {
            if (currentTextStartAddress == -1) {
                currentTextStartAddress = Integer.parseInt(address, 16);
            }

            if (currentTextLength + objCode.length() / 2 > 30) {
                objCodeOutput.append(String.format("T^%06X^%02X%s\n", currentTextStartAddress, currentTextLength, currentTextRecord));
                currentTextRecord = ""; // Reset the current text record
                currentTextStartAddress = Integer.parseInt(address, 16); // New starting address
                currentTextLength = 0; // Reset the length
            }

            currentTextRecord += "^" + objCode; // Append the object code
            currentTextLength += objCode.length() / 2; // Count bytes added
            endingAddress = Integer.parseInt(address, 16); // Update the ending address
        }

        // Handle the END directive
        if (opcode.equals("END")) {
            if (!currentTextRecord.isEmpty()) {
                objCodeOutput.append(String.format("T^%06X^%02X%s\n", currentTextStartAddress, currentTextLength, currentTextRecord));
            }
            objCodeOutput.append(String.format("E^%06X\n", startingAddress));
            break; // End processing on END directive
        }
    }

    // Calculate program length
    int programLength = endingAddress - startingAddress;

    // Update the header record with the correct program length
    objCodeOutput.insert(0, String.format("H^%-6s^%06X^%06X\n", programName, startingAddress, programLength));

    // Combine pass2 output with object code
    machineCode.setLength(0); // Clear previous contents
    machineCode.append(pass2Output); // Append intermediate file with machine code
    machineCode.append(objCodeOutput); // Append object code
}




    // Display output in JTextArea
    private void displayOutput() {
        outputArea.append("\nIntermediate File:\n");
        outputArea.append(intermediateFile.toString());
        outputArea.append("\nSYMTAB:\n");
        for (String label : symtab.keySet()) {
            outputArea.append(label + "\t" + String.format("%04X", symtab.get(label)) + "\n");
        }
        outputArea.append("\nMachine Code:\n");
        outputArea.append(machineCode.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TwoPassAssemblerGUI gui = new TwoPassAssemblerGUI();
            gui.setVisible(true);
        });
    }
}


/* input file
   - START   2000
   - LDA FIVE
   - STA ALPHA
   - LDCH    CHARZ
   - STCH    C1
     ALPHA   RESW    2
     FIVE    WORD    5
     CHARZ   BYTE    C'Z'
      C1  RESB    1
   -  END **

   optab

   START   *
   LDA     03
   STA     0f
   LDCH    53
   STCH    57
    END * */
    