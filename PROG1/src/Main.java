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
        String instruction = args[0].split("#")[0].trim(); // Clips off comments
        String machineCode = assemble(instruction); // Calls assemble method
        System.out.println(machineCode); // Prints output
    }

    private static String assemble(String instruction) {
        String[] parts = instruction.replaceAll("[,()]", " ").split("\\s+");
        //System.out.println(parts[0]);
        //System.out.println(parts[1]);
        //System.out.println(parts[2]);
        String mnemonic = parts[0];
        String opcode = OPCODES.get(mnemonic);

        if (opcode == null) return "00000000"; // Invalid instruction fallback

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
