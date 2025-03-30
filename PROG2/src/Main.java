import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {
    private static final int TEXT_START = 0x00400000;
    private static final int DATA_START = 0x10010000;
    private static Map<String, Integer> labelTable = new HashMap<>();
    private static List<String> textSection = new ArrayList<>();
    private static List<String> dataSection = new ArrayList<>();

    public static void main (String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java MIPSAssembler <inputfile>");
            return;
        }

        String inputFile = args[0];
        parseFile(inputFile);
        resolveLabels();
        writeOutputFiles(inputFile);
    }

    private static void parseFile (String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean inText = false, inData = false;
            int textAddress = TEXT_START, dataAddress = DATA_START;

            while ((line = reader.readLine()) != null) {
                line = line.split("#")[0].trim();
                if (line.isEmpty()) continue;

                if (line.startsWith(".text")) {
                    inText = true;
                    inData = false;
                    continue;
                } else if (line.startsWith(".data")) {
                    inData = true;
                    inText = false;
                    continue;
                }

                if (inText) {
                    if (line.contains(":")) {
                        String[] parts = line.split(":", 2);
                        labelTable.put(parts[0], textAddress); // Trim removed
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            textSection.add(parts[1].trim());
                            textAddress += 4;
                        }
                    } else {
                        textSection.add(line);
                        textAddress += 4;
                    }
                } else if (inData) {
                    if (line.contains(".asciiz")) {
                        String[] parts = line.split("\"", 2);
                        if (parts.length > 1) {
                            String stringData = parts[1] + "\0";
                            byte[] bytes = stringData.getBytes(StandardCharsets.US_ASCII);
                            List<String> hexWords = new ArrayList<>();
                            for (int i = 0; i < bytes.length; i += 4) {
                                int word = 0;
                                for (int j = 0; j < 4 && (i + j) < bytes.length; j++) {
                                    word |= ((bytes[i + j] & 0xFF) << (8 * j));
                                }
                                hexWords.add(String.format("%08x", word));
                            }
                            dataSection.addAll(hexWords);
                        }
                    } else {
                        dataSection.add(line);
                        dataAddress += 4;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("error reading file: " + e.getMessage());
        }
    }

    private static void resolveLabels() {
        List<String> resolvedText = new ArrayList<>();
        System.out.println("Label Table: " + labelTable);

        for (String line : textSection) {
            resolvedText.add(binaryToHex(translateInstruction(line)));
        }

        textSection = resolvedText;
    }

    private static String translateInstruction(String instruction) {
        String[] parts = instruction.split(" ");
        String opcode = parts[0];

        return switch (opcode) {
            case "add" -> encodeRType(0x20, parts[1], parts[2], parts[3]);
            case "sub" -> encodeRType(0x22, parts[1], parts[2], parts[3]);
            case "li" -> encodeLi(parts[1], Integer.parseInt(parts[2]));
            case "beq" -> encodeBranch(0x04, parts[1], parts[2], parts[3]);
            case "bne" -> encodeBranch(0x05, parts[1], parts[2], parts[3]);
            case "j" -> encodeJump(0x02, parts[1]);
            case "move" -> encodeRType(0x20, parts[1], "$zero", parts[2]);
            case "blt" -> encodeBranchLessThan(parts[1], parts[2], parts[3]);
            case "la" -> encodeLa(parts[1], parts[2]);
            default -> "00000000"; // Invalid instruction
        };
    }

    private static String encodeBranchLessThan (String rs, String rt, String label) {
        String slt = encodeRType(0x2A, "$at", rs, rt);
        String bne = encodeBranch(0x05, "$at", "$zero", label);
        return slt + "\n" + bne;
    }

    private static String encodeLa(String reg, String label) {
        Integer address = labelTable.get(label);
        if (address == null) {
            throw new IllegalArgumentException("Undefined label: " + label);
        }
        String lui = "001111" + "00000" + getRegister(reg) + Integer.toBinaryString(address >>> 16);
        String ori = "001101" + getRegister(reg) + getRegister(reg) + Integer.toBinaryString(address & 0xFFFF);
        return lui + "\n" + ori;
    }

    private static String encodeRType (int funct, String rd, String rs, String rt) {
        return "000000" + getRegister(rs) + getRegister(rt) + getRegister(rd) + "00000" + Integer.toBinaryString(funct);
    }

    private static String encodeLi( String reg, int imm) {
        return "001111" + "00000" + getRegister(reg) + Integer.toBinaryString(imm);
    }

    private static String encodeBranch (int opcode, String rs, String rt, String label) {
        int branchAddress = labelTable.get(label);
        int currentPC = TEXT_START + textSection.indexOf(label) * 4;
        int offset = (branchAddress - (currentPC + 4)) / 4;
        return String.format("%6s%5s%5s%16s", Integer.toBinaryString(opcode), getRegister(rs), getRegister(rt), Integer.toBinaryString(offset & 0xFFFF));
    }

    private static String encodeJump (int opcode, String label) {
        int address = (labelTable.get(label) & 0x0FFFFFFF) >>> 2;
        return String.format("%6s%26s", Integer.toBinaryString(opcode), Integer.toBinaryString(address)).replace(" ", "0");
    }

    private static String getRegister(String reg) {
        return switch (reg) {
            case "$zero" -> "00000";
            case "$t0" -> "01000";
            case "$t1" -> "01001";
            case "$t2" -> "01010";
            default -> "00000";
        };
    }

    private static String binaryToHex (String binary) {
        return String.format("%08x", Integer.parseUnsignedInt(binary, 2));
    }

    private static void writeOutputFiles(String inputFile) {
        File parentDir = new File(new File(inputFile).getParent());
        try (PrintWriter textWriter = new PrintWriter(new File(parentDir,inputFile + ".text"));
             PrintWriter dataWriter = new PrintWriter(new File(parentDir,inputFile + ".data"))) {
            for (String line : textSection) textWriter.println(line);
            for (String line : dataSection) dataWriter.println(line);
        } catch (IOException e) {
            System.err.println("Error writing output files: " + e.getMessage());
        }
    }
}
