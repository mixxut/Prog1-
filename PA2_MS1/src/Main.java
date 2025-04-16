public class Main {

    public static void main(String[] args) {
        // Check that exactly one argument is provided
        if (args.length != 1) {
            System.err.println("Usage: java -jar PROG2_Milestone1.jar <8-digit hexadecimal MIPS instruction>");
            System.exit(1);
        }

        // The instruction is passed in as an 8-digit hexadecimal string (no preceding "0x")
        String hexInstruction = args[0];
        // Parse the hex string to a 32-bit value (using long to avoid sign issues)
        long instruction = Long.parseUnsignedLong(hexInstruction, 16);

        // The opcode occupies the top 6 bits [31:26]
        int opcode = (int) ((instruction >>> 26) & 0x3F);

        Instruction instr;

        // If opcode is 0, then it is an R-type instruction (or syscall)
        if (opcode == 0) {
            int rs    = (int) ((instruction >>> 21) & 0x1F);
            int rt    = (int) ((instruction >>> 16) & 0x1F);
            int rd    = (int) ((instruction >>> 11) & 0x1F);
            int shmt  = (int) ((instruction >>> 6)  & 0x1F);
            int funct = (int) (instruction & 0x3F);

            // Check for syscall: it is the only R-type where the funct field is 0x0c
            if (funct == 0x0c) {
                instr = new SyscallInstruction(opcode, funct);
            } else {
                String mnemonic;
                // Determine the mnemonic based on the funct field
                switch (funct) {
                    case 0x20:
                        mnemonic = "add";
                        break;
                    case 0x22:
                        mnemonic = "sub";
                        break;
                    case 0x24:
                        mnemonic = "and";
                        break;
                    case 0x25:
                        mnemonic = "or";
                        break;
                    case 0x2a:
                        mnemonic = "slt";
                        break;
                    default:
                        System.err.println("Unknown R-type function code: " + Integer.toHexString(funct));
                        return;
                }
                instr = new RTypeInstruction(mnemonic, opcode, rs, rt, rd, shmt, funct);
            }
        }
        // Check for J-type: j instruction has opcode 0x02
        else if (opcode == 0x02) {
            // The J-type index is the lower 26 bits.
            int index = (int) (instruction & 0x03FFFFFF);
            instr = new JTypeInstruction("j", opcode, index);
        }
        // Otherwise, this is an I-type instruction
        else {
            int rs = (int) ((instruction >>> 21) & 0x1F);
            int rt = (int) ((instruction >>> 16) & 0x1F);
            int immediate = (int) (instruction & 0xFFFF);
            String mnemonic;
            switch (opcode) {
                case 0x09:
                    mnemonic = "addiu";
                    break;
                case 0x0c:
                    mnemonic = "andi";
                    break;
                case 0x04:
                    mnemonic = "beq";
                    break;
                case 0x05:
                    mnemonic = "bne";
                    break;
                case 0x0f:
                    mnemonic = "lui";
                    // For lui, the rs(base) value is always "00"
                    rs = 0;
                    break;
                case 0x23:
                    mnemonic = "lw";
                    break;
                case 0x0d:
                    mnemonic = "ori";
                    break;
                case 0x2b:
                    mnemonic = "sw";
                    break;
                default:
                    System.err.println("Unknown I-type opcode: " + Integer.toHexString(opcode));
                    return;
            }
            instr = new ITypeInstruction(mnemonic, opcode, rs, rt, immediate);
        }

        // Output the disassembled instruction with a newline.
        System.out.println(instr.toString());
    }

    // Abstract base class for an instruction
    abstract static class Instruction {
        String mnemonic;
        Instruction(String mnemonic) {
            this.mnemonic = mnemonic;
        }
        public abstract String toString();
    }

    // R-type instruction (for add, sub, and, or, slt)
    // Output format:
    // mnemonic {opcode: XX, rs: XX, rt: XX, rd: XX, shmt: XX, funct: XX}
    static class RTypeInstruction extends Instruction {
        int opcode;
        int rs;
        int rt;
        int rd;
        int shmt;
        int funct;
        RTypeInstruction(String mnemonic, int opcode, int rs, int rt, int rd, int shmt, int funct) {
            super(mnemonic);
            this.opcode = opcode;
            this.rs = rs;
            this.rt = rt;
            this.rd = rd;
            this.shmt = shmt;
            this.funct = funct;
        }
        @Override
        public String toString() {
            return String.format("%s {opcode: %02x, rs: %02x, rt: %02x, rd: %02x, shmt: %02x, funct: %02x}",
                    mnemonic, opcode, rs, rt, rd, shmt, funct);
        }
    }

    // I-type instruction (for addiu, andi, beq, bne, lui, lw, ori, sw)
    // Output format:
    // mnemonic {opcode: XX, rs(base): XX, rt: XX, immediate(offset): XXXX}
    static class ITypeInstruction extends Instruction {
        int opcode;
        int rs;
        int rt;
        int immediate;
        ITypeInstruction(String mnemonic, int opcode, int rs, int rt, int immediate) {
            super(mnemonic);
            this.opcode = opcode;
            this.rs = rs;
            this.rt = rt;
            this.immediate = immediate;
        }
        @Override
        public String toString() {
            return String.format("%s {opcode: %02x, rs(base): %02x, rt: %02x, immediate(offset): %04x}",
                    mnemonic, opcode, rs, rt, immediate);
        }
    }

    // J-type instruction (for j)
    // Output format:
    // mnemonic {opcode: XX, index: XXXXXXX}
    static class JTypeInstruction extends Instruction {
        int opcode;
        int index;
        JTypeInstruction(String mnemonic, int opcode, int index) {
            super(mnemonic);
            this.opcode = opcode;
            this.index = index;
        }
        @Override
        public String toString() {
            return String.format("%s {opcode: %02x, index: %07x}",
                    mnemonic, opcode, index);
        }
    }

    // Special case for syscall instruction
    // Output format:
    // syscall {opcode: XX, code: 000000, funct: XX}
    static class SyscallInstruction extends Instruction {
        int opcode;
        int funct;
        SyscallInstruction(int opcode, int funct) {
            super("syscall");
            this.opcode = opcode;
            this.funct = funct;
        }
        @Override
        public String toString() {
            return String.format("%s {opcode: %02x, code: 000000, funct: %02x}",
                    mnemonic, opcode, funct);
        }
    }
}
