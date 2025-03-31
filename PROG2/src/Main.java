import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final Map<String, String> REGISTER_MAP = Map.ofEntries( // Register number relationships
            Map.entry("$zero", "00000"), Map.entry("$at", "00001"), Map.entry("$v0", "00010"),
            Map.entry("$v1", "00011"), Map.entry("$a0", "00100"), Map.entry("$a1", "00101"),
            Map.entry("$a2", "00110"), Map.entry("$a3", "00111"), Map.entry("$t0", "01000"),
            Map.entry("$t1", "01001"), Map.entry("$t2", "01010"), Map.entry("$t3", "01011"),
            Map.entry("$t4", "01100"), Map.entry("$t5", "01101"), Map.entry("$t6", "01110"),
            Map.entry("$t7", "01111"), Map.entry("$s0", "10000"), Map.entry("$s1", "10001"),
            Map.entry("$s2", "10010"), Map.entry("$s3", "10011"), Map.entry("$s4", "10100"),
            Map.entry("$s5", "10101"), Map.entry("$s6", "10110"), Map.entry("$s7", "10111"),
            Map.entry("$t8", "11000"), Map.entry("$t9", "11001"), Map.entry("$k0", "11010"),
            Map.entry("$k1", "11011"), Map.entry("$gp", "11100"), Map.entry("$sp", "11101"),
            Map.entry("$fp", "11110"), Map.entry("$ra", "11111")
    );

    private static final Map<String, String> OPCODES = Map.ofEntries( // Opcodes to determine instruction
            Map.entry("add", "000000"), Map.entry("addiu", "001001"), Map.entry("and", "000000"), Map.entry("andi", "001100"),
            Map.entry("beq", "000100"), Map.entry("bne", "000101"), Map.entry("j", "000010"), Map.entry("lui", "001111"),
            Map.entry("lw", "100011"), Map.entry("or", "000000"), Map.entry("ori", "001101"), Map.entry("slt", "000000"),
            Map.entry("sub", "000000"), Map.entry("sw", "101011"), Map.entry("syscall", "000000")
    );

    private static final Map<String, String> FUNCT_CODES = Map.of( // Funct codes for R type instructions
            "add", "100000", "and", "100100", "or", "100101", "slt", "101010",
            "sub", "100010", "syscall", "001100"
    );

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main input.asm");
            return;
        }

        String inputFile = args[0];
        String baseName = inputFile.substring(0, inputFile.lastIndexOf("."));
        String textOutputFile = baseName + ".text"; // text Output File name
        String dataOutputFile = baseName + ".data"; // data Output File name

        try {
            // Read the input file
            List<String> lines = Files.readAllLines(Paths.get(inputFile));

            // Process sections
            processFile(lines, textOutputFile, dataOutputFile);
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }

    }

    private static void processFile(List<String> lines, String textOutputFile, String dataOutputFile) throws IOException {
        List<String> dataSection = new ArrayList<>();
        List<String> textSection = new ArrayList<>();

        boolean inDataSection = false;
        boolean inTextSection = false;

        // First pass - separate sections
        for (String line : lines) {
            line = line.split("#")[0].trim(); // Remove comments
            if (line.isEmpty()) continue;

            if (line.equals(".data")) {
                inDataSection = true;
                inTextSection = false;
                continue;
            } else if (line.equals(".text")) {
                inDataSection = false;
                inTextSection = true;
                continue;
            }

            if (inDataSection) {
                dataSection.add(line);
            } else if (inTextSection) {
                textSection.add(line);
            }
        }

        // Process data section
        Map<String, Integer> dataLabels = processDataSection(dataSection, dataOutputFile);

        // Process text section (using data labels for references)
        processTextSection(textSection, textOutputFile, dataLabels);
    }

    private static Map<String, Integer> processDataSection(List<String> dataSection, String outputFile) throws IOException {
        Map<String, Integer> labels = new HashMap<>();
        List<String> hexOutputs = new ArrayList<>();
        int currentAddress = 0x10010000; // Data section starts at this address

        for (String line : dataSection) {
            if (line.isEmpty()) continue;

            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                String label = parts[0].trim();
                String dataDeclaration = parts.length > 1 ? parts[1].trim() : "";

                // Record label address
                labels.put(label, currentAddress);

                if (!dataDeclaration.isEmpty()) {
                    // Process the data declaration
                    if (dataDeclaration.startsWith(".asciiz")) {
                        // Extract the string between quotes
                        String str = dataDeclaration.substring(dataDeclaration.indexOf("\"") + 1,
                                dataDeclaration.lastIndexOf("\""));

                        // Convert string to bytes and add null terminator
                        byte[] bytes = (str + "\0").getBytes();

                        // Process bytes in little-endian format, 4 bytes at a time
                        for (int i = 0; i < bytes.length; i += 4) {
                            int word = 0;
                            for (int j = 0; j < 4 && i + j < bytes.length; j++) {
                                word |= ((bytes[i + j] & 0xFF) << (j * 8));
                            }
                            hexOutputs.add(String.format("%08x", word));
                            currentAddress += 4;
                        }
                    }
                }
            }
        }

        // Write data section output
        Files.write(Paths.get(outputFile), hexOutputs);

        return labels;
    }

    private static void processTextSection(List<String> textSection, String outputFile,
                                           Map<String, Integer> dataLabels) throws IOException {
        Map<String, Integer> textLabels = new HashMap<>();
        List<String> expandedInstructions = new ArrayList<>();
        List<String> hexOutputs = new ArrayList<>();
        int currentAddress = 0x00400000; // Text section starts at this address

        // First pass - identify labels and expand pseudo-instructions
        for (String line : textSection) {
            if (line.isEmpty()) continue;

            // Handle label-only line
            if (line.endsWith(":")) {
                String label = line.substring(0, line.indexOf(":")).trim();
                textLabels.put(label, currentAddress);
                continue;
            }

            // Handle line with label and instruction
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                String label = parts[0].trim();
                String instruction = parts[1].trim();

                textLabels.put(label, currentAddress);

                if (!instruction.isEmpty()) {
                    List<String> expanded = expandPseudoInstructions(instruction);
                    expandedInstructions.addAll(expanded);
                    currentAddress += expanded.size() * 4;
                }
            } else {
                // Normal instruction
                List<String> expanded = expandPseudoInstructions(line);
                expandedInstructions.addAll(expanded);
                currentAddress += expanded.size() * 4;
            }
        }

        // Reset address for second pass
        currentAddress = 0x00400000;

        // Second pass - assemble instructions with correct addresses
        for (String instruction : expandedInstructions) {
            String machineCode = assembleWithLabels(instruction, currentAddress, textLabels, dataLabels);
            hexOutputs.add(machineCode);
            currentAddress += 4;
        }

        // Write text section output
        Files.write(Paths.get(outputFile), hexOutputs);
    }

    private static List<String> expandPseudoInstructions(String instruction) {
        List<String> result = new ArrayList<>();
        String[] parts = instruction.replaceAll("[,()]", " ").split("\\s+");
        String mnemonic = parts[0];

        switch (mnemonic) {
            case "li":
                // li $t0, imm -> addiu $t0, $zero, imm (for small values)
                // For large values: lui + ori
                int imm = Integer.decode(parts[2]);
                if (imm >= -32768 && imm <= 32767) {
                    result.add("addiu " + parts[1] + ", $zero, " + imm);
                } else {
                    int upper = (imm >> 16) & 0xFFFF;
                    int lower = imm & 0xFFFF;
                    result.add("lui $at, " + upper);
                    result.add("ori " + parts[1] + ", $at, " + lower);
                }
                break;

            case "la":
                // la $t0, label -> lui $at + ori
                // (will need to be resolved in second pass)
                result.add("lui $at, UPPER_" + parts[2]);
                result.add("ori " + parts[1] + ", $at, LOWER_" + parts[2]);
                break;

            case "blt":
                // blt $t0, $t1, label -> slt $at, $t0, $t1 + bne $at, $zero, label
                result.add("slt $at, " + parts[1] + ", " + parts[2]);
                result.add("bne $at, $zero, " + parts[3]);
                break;

            case "move":
                // move $t0, $t1 -> add $t0, $zero, $t1
                result.add("add " + parts[1] + ", $zero, " + parts[2]);
                break;

            default:
                result.add(instruction);
                break;
        }

        return result;
    }

    private static String assembleWithLabels(String instruction, int currentAddress,
                                             Map<String, Integer> textLabels,
                                             Map<String, Integer> dataLabels) {
        String[] parts = instruction.replaceAll("[,()]", " ").split("\\s+");
        String mnemonic = parts[0];
        String opcode = OPCODES.get(mnemonic);

        if (opcode == null) return "00000000"; // Invalid instruction

        // Handle label references in branch/jump instructions
        if (mnemonic.equals("j")) {
            if (textLabels.containsKey(parts[1])) {
                int targetAddress = textLabels.get(parts[1]);
                // For J instructions, need the word address
                int wordAddress = targetAddress >> 2;
                return binaryToHex(opcode + String.format("%26s",
                        Integer.toBinaryString(wordAddress)).replace(' ', '0'));
            }
        } else if (mnemonic.equals("beq") || mnemonic.equals("bne")) {
            String rs = REGISTER_MAP.get(parts[1]);
            String rt = REGISTER_MAP.get(parts[2]);

            if (textLabels.containsKey(parts[3])) {
                int targetAddress = textLabels.get(parts[3]);
                // Calculate branch offset (in words)
                int offset = (targetAddress - (currentAddress + 4)) >> 2;

                String immediateBinary = String.format("%16s",
                        Integer.toBinaryString(offset & 0xFFFF)).replace(' ', '0');

                return binaryToHex(opcode + rs + rt + immediateBinary);
            }
        } else if (instruction.contains("UPPER_") || instruction.contains("LOWER_")) {
            // Handle la pseudo-instruction placeholder
            String targetLabel = "";
            int value = 0;

            if (mnemonic.equals("lui") && parts[2].startsWith("UPPER_")) {
                targetLabel = parts[2].substring(6);
                if (dataLabels.containsKey(targetLabel)) {
                    value = (dataLabels.get(targetLabel) >> 16) & 0xFFFF;
                }
            } else if (mnemonic.equals("ori") && parts[3].startsWith("LOWER_")) {
                targetLabel = parts[3].substring(6);
                if (dataLabels.containsKey(targetLabel)) {
                    value = dataLabels.get(targetLabel) & 0xFFFF;
                }
            }

            // Process as normal with resolved value
            parts[parts.length - 1] = String.valueOf(value);
        }


        if (FUNCT_CODES.containsKey(mnemonic)) { // R-type
            if (mnemonic.equals("syscall")) {
                return "0000000c";
            }
            String rd = REGISTER_MAP.get(parts[1]);
            String rs = REGISTER_MAP.get(parts[2]);
            String rt = REGISTER_MAP.get(parts[3]);
            return binaryToHex(opcode + rs + rt + rd + "00000" + FUNCT_CODES.get(mnemonic));
        } else if (mnemonic.equals("j")) { // J-type
            int address = Integer.parseInt(parts[1].replace("0x", ""), 16);
            return binaryToHex(opcode + String.format("%26s", Integer.toBinaryString(address)).replace(' ', '0'));
        } else if (mnemonic.equals("lui")) { // Special case for lui I type
            String rt = REGISTER_MAP.get(parts[1]); // lui has only rt
            String rs = "00000"; // rs is always $zero (00000) for lui
            int immediate = Integer.decode(parts[2]); // Immediate value
            return binaryToHex(opcode + rs + rt + String.format("%16s", Integer.toBinaryString(immediate & 0xFFFF)).replace(' ', '0'));
        } else if (mnemonic.equals("lw") || mnemonic.equals("sw")) { // Load/store instructions
            String rt = REGISTER_MAP.get(parts[1]); // Destination register
            String rs = REGISTER_MAP.get(parts[3]); // Base register (inside parentheses)
            int immediate = Integer.decode(parts[2]); // Offset
            return binaryToHex(opcode + rs + rt + String.format("%16s", Integer.toBinaryString(immediate & 0xFFFF)).replace(' ', '0'));
        } else { // General I-type instructions, including beq
            String rs = REGISTER_MAP.get(parts[1]); // Corrected order for I-type
            String rt = REGISTER_MAP.get(parts[2]);
            int immediate = Integer.decode(parts[3]);

            if (mnemonic.equals("ori")) {
                String temp = rs;
                rs = rt;
                rt = temp;
            }

            // Ensure immediate is 16-bit signed
            String immediateBinary = String.format("%16s", Integer.toBinaryString(immediate)).replace(' ', '0');

            // If the number is negative, apply sign extension
            if (immediate < 0) {
                immediateBinary = immediateBinary.substring(immediateBinary.length() - 16);
            }

            System.out.println(opcode + rs + rt + immediateBinary);
            return binaryToHex(opcode + rs + rt + immediateBinary);
        }
    }

    private static String binaryToHex(String binary) {
        return String.format("%08x", Long.parseLong(binary, 2));
    }
}
