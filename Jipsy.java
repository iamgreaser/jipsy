import java.io.*;
import java.util.*;

public class Jipsy
{
	private String outbuf = "";

	private int[] ram;
	private int[] regs;
	private int rlo, rhi;
	protected int ram_bits;
	protected int pc;
	protected int cycles;
	private int cycle_wait;
	private int pf0;
	private int pf0_pc;
	private int reset_pc = 0x400050;

	private final boolean DEBUG_WRITES_ENABLE = false;
	private static final boolean DEBUG_READS = false;
	private static final boolean DEBUG_JUMPS = false;
	private static final boolean DEBUG_OP_REGDUMP = false;
	private static final int OUTBUF_LEN = 1;

	private boolean DEBUG_WRITES = false;

	public void use_cycles(int count)
	{
		assert(count >= 0);
		this.cycles += count;
	}

	public void set_sp(int sp)
	{
		this.regs[29] = sp;
	}

	public void set_gp(int gp)
	{
		this.regs[28] = gp;
	}

	public void set_reset_pc(int pc)
	{
		this.reset_pc = pc;
	}

	public void reset()
	{
		this.pc = this.reset_pc;
		this.pf0 = this.mem_read_32(this.pc);
		this.pc += 4;
	}

	protected Jipsy()
	{
		this.ram_bits = 0;
		this.ram = null;
		this.regs = new int[32];
		this.rlo = 0;
		this.rhi = 0;
		this.reset();
		this.cycles = 0;
		this.cycle_wait = 0;
		this.regs[4] = 1;
		this.regs[5] = 0x00001000;

	}

	public Jipsy(int ram_bits_)
	{
		assert(ram_bits_ >= 2);
		assert(ram_bits_ <= 28); // cap at 256MB
		this.ram_bits = ram_bits_;
		this.ram = new int[1<<(ram_bits_-2)];
		this.regs = new int[32];
		this.reset();
		this.cycles = 0;
		this.cycle_wait = 0;
	}

	// FIXME: unaligned accesses are as per the ARM7TDMI in the GBA
	public int mem_read_32(int addr_)
	{
		if(DEBUG_READS)
		System.out.printf("read32 %08X\n", addr_);

		if(addr_ >= 0x1FF00000)
		switch(addr_&0xFFFFF)
		{
			case 0x00004:
				try
				{
					return System.in.read();
				} catch(IOException _e) {
					return -2;
				}
			default:
				return 0;
		}

		int v = this.ram[addr_>>>2];
		int b = (addr_ & 3);
		if(b == 0) return v;
		b <<= 3;
		int v0 = (v>>>b);
		int v1 = (v<<(32-b));
		return v0|v1;
	}

	public short mem_read_16(int addr_)
	{
		if(DEBUG_READS)
		System.out.printf("read16 %08X\n", addr_);

		return (short)mem_read_32(addr_);
	}

	public byte mem_read_8(int addr_)
	{
		if(DEBUG_READS)
		System.out.printf("read8 %08X\n", addr_);

		return (byte)mem_read_32(addr_);
	}

	public String mem_read_cstr(int addr_)
	{
		int len = 0;
		ByteArrayOutputStream bfp = new ByteArrayOutputStream();

		// cap it here
		while(len < 65536)
		{
			byte b = mem_read_8(addr_ + len);
			if(b == 0) break;
			bfp.write(0xFF&(int)b);
			len++;
		}

		//return bfp.toString("UTF-8");
		return bfp.toString();
	}

	public void mem_write_32_masked(int addr_, int data_, int mask_)
	{
		if(DEBUG_WRITES)
		System.out.printf("write32_masked %08X %08X %08X %08X\n", addr_, data_, mask_, this.ram[addr_>>>2]);

		if((addr_&3) != 0)
		{
			System.out.printf("PC ~ %08X\n", this.pc - 8);
			throw new RuntimeException("misaligned 32-bit write");
		}

		this.ram[addr_ >>> 2] &= ~mask_;
		this.ram[addr_ >>> 2] |= data_ & mask_;
	}

	public void mem_write_32(int addr_, int data_)
	{
		if(DEBUG_WRITES)
		System.out.printf("write32 %08X %08X\n", addr_, data_);

		if((addr_&3) != 0)
		{
			System.out.printf("PC ~ %08X\n", this.pc - 8);
			throw new RuntimeException("misaligned 32-bit write");
		}
		this.ram[addr_ >>> 2] = data_;
	}

	public void mem_write_16(int addr_, short data_)
	{
		if(DEBUG_WRITES)
		System.out.printf("write16 %08X %04X\n", addr_, data_);

		if((addr_&1) != 0) throw new RuntimeException("misaligned 16-bit write");
		int b = (addr_>>1) & 1;
		int a = addr_ >>> 2;
		switch(b)
		{
			case 0:
				this.ram[a] = (this.ram[a] & 0xFFFF0000) | (((int)data_) & 0xFFFF);
				break;
			case 1:
				this.ram[a] = (this.ram[a] & 0x0000FFFF) | (((int)data_) << 16);
				break;
		}
	}

	public void mem_write_8(int addr_, byte data_)
	{
		if(DEBUG_WRITES)
		System.out.printf("write8 %08X %02X\n", addr_, data_);

		if(addr_ >= 0x1FF00000)
		switch(addr_&0xFFFFF)
		{
			case 0x00004:
				//System.out.printf("%c", data_);
				this.outbuf += (char)data_;
				if(this.outbuf.length() >= OUTBUF_LEN)
				{
					System.out.print(this.outbuf);
					this.outbuf = "";
				}
				return;
			default:
				return;
		}

		int b = addr_ & 3;
		int a = addr_ >>> 2;
		b <<= 3;
		int v = ((int)data_) & 0xFF;
		int m = 0xFF << b;

		this.ram[a] = (this.ram[a] & ~m) | (v << b);
	}

	protected void swi(int op_pc, int op)
	{
		// TODO!
	}

	public void run_op()
	{
		// Fetch
		int op = this.pf0;
		int pc = this.pf0_pc;
		int new_op = this.mem_read_32(this.pc);
		this.pf0 = new_op;
		this.pf0_pc = this.pc;
		this.pc += 4;
		this.cycles += 1;

		int otyp0 = (op>>>26);
		int rs = (op>>>21)&0x1F;
		int rt = (op>>>16)&0x1F;
		int rd = (op>>>11)&0x1F;
		int sh = (op>>>6)&0x1F;
		int otyp1 = (op)&0x3F;

		// we're pretending that the load/store delay slot isn't a thing
		// the reason for this is it's unreliable especially in the context of interrupts
		// thus i'm pretty sure compilers avoid it

		int tmp0, tmp1;

		if(DEBUG_OP_REGDUMP)
		System.out.printf("%08X\n", op);

		if(otyp0 == 0x00) switch(otyp1) {

			case 0x00: // SLL
				if(rd != 0) this.regs[rd] = this.regs[rt] << sh;
				break;
			case 0x02: // SRL
				if(rd != 0) this.regs[rd] = this.regs[rt] >>> sh;
				break;
			case 0x03: // SRA
				if(rd != 0) this.regs[rd] = this.regs[rt] >> sh;
				break;

			case 0x04: // SLLV
				if(rd != 0) this.regs[rd] = this.regs[rt] << (this.regs[rs] & 0x1F);
				break;
			case 0x06: // SRLV
				if(rd != 0) this.regs[rd] = this.regs[rt] >>> (this.regs[rs] & 0x1F);
				break;
			case 0x07: // SRAV
				if(rd != 0) this.regs[rd] = this.regs[rt] >> (this.regs[rs] & 0x1F);
				break;

			case 0x09: // JALR
				this.regs[rd] = pc+8;
				if(DEBUG_JUMPS)
				System.out.printf("JALR\n");
			case 0x08: // JR
				if(DEBUG_JUMPS)
				System.out.printf("JR %2d %2d %08X\n", rs, rd, this.regs[rs]);
				this.pc = this.regs[rs];
				break;

			// XXX: do we pipeline lo/hi and introduce delays?
			case 0x10: // MFHI
				if(rd != 0) this.regs[rd] = this.rhi;
				break;
			case 0x12: // MFLO
				if(rd != 0) this.regs[rd] = this.rlo;
				break;

			case 0x18: // MULT
				{
					long va = (long)this.regs[rs];
					long vb = (long)this.regs[rt];
					long result = va*vb;
					this.rlo = (int)result;
					this.rhi = (int)(result>>32);

					if(va >= -0x800 && va < 0x800)
						this.cycles += 6-1;
					else if(va >= -0x100000 && va < 0x100000)
						this.cycles += 9-1;
					else
						this.cycles += 13-1;
				}
				break;
			case 0x19: // MULTU
				{
					long va = 0xFFFFFFFFL&(long)this.regs[rs];
					long vb = 0xFFFFFFFFL&(long)this.regs[rt];
					long result = va*vb;
					this.rlo = (int)result;
					this.rhi = (int)(result>>32);

					if(va >= 0 && va < 0x800)
						this.cycles += 6-1;
					else if(va >= 0 && va < 0x100000)
						this.cycles += 9-1;
					else
						this.cycles += 13-1;
				}
				break;
			case 0x1A: // DIV
				if(this.regs[rt] == 0)
				{
					if(this.regs[rs] >= 0)
						this.rlo = -1;
					else
						this.rlo = 1;

					this.rhi = this.regs[rs];

				} else {
					long vnum = 0xFFFFFFFFL&(long)this.regs[rs];
					long vdenom = 0xFFFFFFFFL&(long)this.regs[rt];

					// TODO: figure out % behaviour when negative
					this.rlo = (int)(vnum / vdenom);
					this.rhi = (int)(vnum % vdenom);
				}

				this.cycles += 36-1;
				break;
			case 0x1B: // DIVU
				if(this.regs[rt] == 0)
				{
					this.rlo = 0xFFFFFFFF;
					this.rhi = this.regs[rs];

				} else {
					long vnum = 0xFFFFFFFFL&(long)this.regs[rs];
					long vdenom = 0xFFFFFFFFL&(long)this.regs[rt];

					this.rlo = (int)(vnum / vdenom);
					this.rhi = (int)(vnum % vdenom);
				}

				//System.out.printf("DIVU %d %d -> %d %d\n", this.regs[rs], this.regs[rt], this.rlo, this.rhi);
				this.cycles += 36-1;
				break;

			case 0x20: // ADD
			case 0x21: // ADDU
				if(rd != 0) this.regs[rd] = this.regs[rs] + this.regs[rt];
				break;

			case 0x22: // SUB
			case 0x23: // SUBU
				if(rd != 0) this.regs[rd] = this.regs[rs] - this.regs[rt];
				break;

			case 0x24: // AND
				if(rd != 0) this.regs[rd] = this.regs[rs] & this.regs[rt];
				break;
			case 0x25: // OR
				if(rd != 0) this.regs[rd] = this.regs[rs] | this.regs[rt];
				break;
			case 0x26: // XOR
				if(rd != 0) this.regs[rd] = this.regs[rs] ^ this.regs[rt];
				break;
			case 0x27: // NOR
				if(rd != 0) this.regs[rd] = this.regs[rs] | ~this.regs[rt];
				break;

			case 0x2A: // SLT
				if(rd != 0) this.regs[rd] =
					(this.regs[rs] < this.regs[rt] ? 1 : 0);
				break;
			case 0x2B: // SLTU
				if(rd != 0) this.regs[rd] =
					(this.regs[rs]+0x80000000 < this.regs[rt]+0x80000000 ? 1 : 0);
				break;

			default:
				System.out.printf("%08X: %08X %02X\n", pc, op, otyp1);
				throw new RuntimeException("unsupported SPECIAL op");

		} else if(otyp0 == 0x01) switch(rt) {

			case 0x00: // BLTZ
				if(this.regs[rs] <  0)
					this.pc = pc + 4 + (((int)(short)op)<<2);
				break;
			case 0x01: // BGEZ
				if(this.regs[rs] >= 0)
					this.pc = pc + 4 + (((int)(short)op)<<2);
				break;

			case 0x10: // BLTZAL
				if(this.regs[rs] <  0)
				{
					this.regs[31] = pc + 8;
					this.pc = pc + 4 + (((int)(short)op)<<2);
				}
				break;
			case 0x11: // BGEZAL
				if(this.regs[rs] >= 0)
				{
					this.regs[31] = pc + 8;
					this.pc = pc + 4 + (((int)(short)op)<<2);
				}
				break;
			default:
				System.out.printf("%08X: %08X %02X\n", pc, op, rt);
				throw new RuntimeException("unsupported BRANCH op");
		
		} else switch(otyp0) {

			case 0x03: // JAL
				this.regs[31] = pc + 8;
			case 0x02: // J
				this.pc = (pc & 0xF0000000)|((op&((1<<26)-1))<<2);
				break;

			case 0x04: // BEQ
				if(this.regs[rs] == this.regs[rt]) this.pc = pc + 4 + (((int)(short)op)<<2);
				break;
			case 0x05: // BNE
				if(this.regs[rs] != this.regs[rt]) this.pc = pc + 4 + (((int)(short)op)<<2);
				break;
			case 0x06: // BLEZ
				if(this.regs[rs] <= 0) this.pc = pc + 4 + (((int)(short)op)<<2);
				break;
			case 0x07: // BGTZ
				if(this.regs[rs] >  0) this.pc = pc + 4 + (((int)(short)op)<<2);
				break;

			// TODO: trap on non-U arithmetic ops
			case 0x08: // ADDI
			case 0x09: // ADDIU
				if(rt != 0) this.regs[rt] = this.regs[rs] + (int)(short)op;
				break;

			case 0x0A: // SLTI
				if(rt != 0) this.regs[rt] =
					(this.regs[rs] < (int)(short)op ? 1 : 0);
				break;
			case 0x0B: // SLTIU
				if(rt != 0) this.regs[rt] =
					(this.regs[rs]+0x80000000 < ((int)(short)op)+0x80000000 ? 1 : 0);
				break;

			case 0x0C: // ANDI
				if(rt != 0) this.regs[rt] = this.regs[rs] & (op&0xFFFF);
				break;
			case 0x0D: // ORI
				if(rt != 0) this.regs[rt] = this.regs[rs] | (op&0xFFFF);
				break;
			case 0x0E: // XORI
				if(rt != 0) this.regs[rt] = this.regs[rs] ^ (op&0xFFFF);
				break;
			case 0x0F: // LUI
				if(rt != 0) this.regs[rt] = (op&0xFFFF)<<16;
				break;

			/*
			case 0x10: // COP0
				// TODO!
				System.out.printf("%08X: COP0 %02X\n", pc, op);
				break;
			*/

			case 0x20: // LB
				tmp0 = (int)this.mem_read_8(this.regs[rs] + (int)(short)op);
				if(rt != 0) this.regs[rt] = tmp0;
				this.cycles += 1;
				break;
			case 0x21: // LH
				tmp0 = (int)this.mem_read_16(this.regs[rs] + (int)(short)op);
				if(rt != 0) this.regs[rt] = tmp0;
				this.cycles += 1;
				break;
			case 0x23: // LW
				tmp0 = this.mem_read_32(this.regs[rs] + (int)(short)op);
				if(rt != 0) this.regs[rt] = tmp0;
				this.cycles += 1;
				break;
			case 0x24: // LBU
				tmp0 = 0xFF&(int)this.mem_read_8(this.regs[rs] + (int)(short)op);
				if(rt != 0) this.regs[rt] = tmp0;
				this.cycles += 1;
				break;
			case 0x25: // LHU
				tmp0 = 0xFFFF&(int)this.mem_read_16(this.regs[rs] + (int)(short)op);
				if(rt != 0) this.regs[rt] = tmp0;
				this.cycles += 1;
				break;

			case 0x28: // SB
				this.mem_write_8(this.regs[rs] + (int)(short)op, (byte)this.regs[rt]);
				this.cycles += 1;
				break;
			case 0x29: // SH
				//System.out.printf("%08X %08X\n", this.regs[rs], (int)(short)op);
				this.mem_write_16(this.regs[rs] + (int)(short)op, (short)this.regs[rt]);
				this.cycles += 1;
				break;
			case 0x2B: // SW
				this.mem_write_32(this.regs[rs] + (int)(short)op, this.regs[rt]);
				this.cycles += 1;
				break;

			// Keeping this section separate to ensure sanity.
			//
			// Repeat after me: 4,814,976
			// Also repeat after me: 2006-12-23
			// And one more: Thank you for the early Christmas present
			//
			// TODO: abuse pipeline bypass
			case 0x22: // LWL
				tmp1 = this.regs[rs] + (int)(short)op;
				tmp0 = this.mem_read_32(tmp1&~3);
				tmp1 ^= 3;
				this.cycles += 1;
				if(rt != 0) this.regs[rt] = (this.regs[rt] & ~(0xFFFFFFFF<<((tmp1&3)<<3)))
					| (tmp0<<((tmp1&3)<<3));
				//System.out.printf("LWL %08X %d\n", this.regs[rt], tmp1&3);
				break;
			case 0x26: // LWR
				tmp1 = this.regs[rs] + (int)(short)op;
				tmp0 = this.mem_read_32(tmp1&~3);
				this.cycles += 1;
				if(rt != 0) this.regs[rt] = (this.regs[rt] & ~(0xFFFFFFFF>>>((tmp1&3)<<3))
					| (tmp0>>>((tmp1&3)<<3)));
				//System.out.printf("LWR %08X %d\n", this.regs[rt], tmp1&3);
				break;

			// Note from psx-spx:
			//
			// The CPU has four separate byte-access signals, so, within a 32bit location,
			// it can transfer all fragments of Rt at once (including for odd 24bit amounts).
			// ^ this is the critical point
			//
			// The transferred data is not zero- or sign-expanded,
			// eg. when transferring 8bit data,
			// the other 24bit of Rt and [mem] will remain intact.
			// ^ this is the not so critical point as it's almost obvious
			case 0x2A: // SWL
				tmp1 = this.regs[rs] + (int)(short)op;
				tmp0 = this.regs[rt];
				tmp1 ^= 3;
				//System.out.printf("SWL %d\n", tmp1&3);
				this.mem_write_32_masked(tmp1&~3, tmp0>>>((tmp1&3)<<3),
					0xFFFFFFFF>>>((tmp1&3)<<3));
				this.cycles += 1;
				break;
			case 0x2E: // SWR
				tmp1 = this.regs[rs] + (int)(short)op;
				tmp0 = this.regs[rt];
				//System.out.printf("SWR %d\n", tmp1&3);
				this.mem_write_32_masked(tmp1&~3, tmp0<<((tmp1&3)<<3),
					0xFFFFFFFF<<((tmp1&3)<<3));
				this.cycles += 1;
				break;

			default:
				System.out.printf("%08X: %08X %02X\n", pc, op, otyp0);
				throw new RuntimeException("unsupported op");
		}
	}

	public void run_cycles(int ccount)
	{
		DEBUG_WRITES = DEBUG_WRITES_ENABLE;

		assert(ccount > 0);
		assert(ccount < 0x40000000);

		this.outbuf = "";
		int cyc_end = this.cycles + ccount - this.cycle_wait;

		while((cyc_end - this.cycles) >= 0 && this.pc > 0x100)
		{
			int pc = this.pc;
			this.run_op();
			if(DEBUG_OP_REGDUMP)
			{
				System.out.printf("%08X:", pc-4);
				for(int i = 1; i < 16; i++)
					System.out.printf(" %08X", this.regs[i]);
				System.out.printf("\n");
				for(int i = 16; i < 32; i++)
					System.out.printf(" %08X", this.regs[i]);
				System.out.printf("\n");
			}
		}

		if(this.outbuf.length() > 0)
		{
			System.out.print(this.outbuf);
			this.outbuf = "";
		}

		if(this.pc <= 0x100)
		{
			if(true)
			{
				System.out.printf("%08X:", this.pc-4);
				for(int i = 1; i < 16; i++)
					System.out.printf(" %08X", this.regs[i]);
				System.out.printf("\n");
				for(int i = 16; i < 32; i++)
					System.out.printf(" %08X", this.regs[i]);
				System.out.printf("\n");
			}

			System.exit(0);
		}

		this.cycle_wait = this.cycles - cyc_end;

		if(false)
		{
			System.out.printf("%08X:", this.pc-4);
			for(int i = 1; i < 16; i++)
				System.out.printf(" %08X", this.regs[i]);
			System.out.printf("\n");
			for(int i = 16; i < 32; i++)
				System.out.printf(" %08X", this.regs[i]);
			System.out.printf("\n");
		}
	}

	public static short bswap16(short v)
	{
		v = (short)(((v>>>8)&0xFF)|(v<<8));
		return v;
	}

	public static int bswap32(int v)
	{
		v = (v>>>16)|(v<<16);
		v = ((v&0xFF00FF00)>>>8)|((v&0x00FF00FF)<<8);

		return v;
	}

	public static void main(String[] args) throws Exception
	{
		Jipsy mips = new Jipsy(23);
		int sp = (1<<17)-0x18000;
		//int gp = (1<<17)-0x08000;
		mips.set_sp(sp);
		//mips.set_gp(gp);

		RandomAccessFile fp = new RandomAccessFile(args[0], "r");

		// Read ELF identifier
		boolean iself = true;
		if(fp.readByte() != 0x7F) iself = false;
		if(fp.readByte() != 'E') iself = false;
		if(fp.readByte() != 'L') iself = false;
		if(fp.readByte() != 'F') iself = false;
		if(fp.readByte() != 0x01) iself = false; // 32
		if(fp.readByte() != 0x01) iself = false; // LE
		if(fp.readByte() != 0x01) iself = false; // current
		if(fp.readByte() != 0x00) iself = false; // arch
		for(int i = 0; i < 8; i++)
			if(fp.readByte() != 0x00) iself = false; // PAD
		if(!iself)
			throw new RuntimeException("not a valid MIPS ELF32LE binary");

		// Read ELF header
		//
		// Your friendly reminder that Java is one of the worst languages
		// ever for serialising data. Lua 5.1 also sucks for it.
		//
		// Lua 5.3 has pack/unpack in the stdlib. Not sure about 5.2.
		//
		// In other words, you can shove your object orientation up your arse.

		int e_type = bswap16(fp.readShort());
		int e_machine = bswap16(fp.readShort());
		int e_version = bswap32(fp.readInt());
		if(e_type != 0x0002) throw new RuntimeException("not an ET_EXEC ELF binary");
		if(e_machine != 0x0008) throw new RuntimeException("not a MIPS ELF binary");
		if(e_version != 0x00000001) throw new RuntimeException("not a valid ELF v1 binary");

		int e_entry = bswap32(fp.readInt());
		int e_phoff = bswap32(fp.readInt());
		int e_shoff = bswap32(fp.readInt());
		int e_flags = bswap32(fp.readInt());
		System.out.printf("entry point = %08X\n", e_entry);
		System.out.printf("flags = %08X\n", e_flags);

		int e_ehsize = bswap16(fp.readShort());
		int e_phentsize = bswap16(fp.readShort());
		int e_phnum = bswap16(fp.readShort());
		int e_shentsize = bswap16(fp.readShort());
		int e_shnum = bswap16(fp.readShort());
		int e_shstrndx = bswap16(fp.readShort());

		if(e_ehsize != 0x34) throw new RuntimeException("invalid header length");
		if(e_phentsize != 0x20) throw new RuntimeException("invalid program header entry size");

		// Read program headers
		for(int i = 0; i < e_phnum; i++)
		{
			fp.seek(e_phoff + i*e_phentsize);

			int p_type = bswap32(fp.readInt());
			int p_offset = bswap32(fp.readInt());
			int p_vaddr = bswap32(fp.readInt());
			int p_paddr = bswap32(fp.readInt());
			int p_filesz = bswap32(fp.readInt());
			int p_memsz = bswap32(fp.readInt());
			int p_flags = bswap32(fp.readInt());
			int p_align = bswap32(fp.readInt());

			if(p_type == 0x00000001) // PT_LOAD
			{
				fp.seek(p_offset);
				int p = p_vaddr;
				for(int j = 0; j < p_filesz; j++)
				{
					//try
					{
						byte v = fp.readByte();
						mips.mem_write_8(p++, v);
					//} catch(EOFException _e) {
					//	break;
					}
				}
			}
		}

		int p = 0x00001000;
		mips.mem_write_32(p, 0x00001008); p += 4;
		mips.mem_write_32(p, 0x00000000); p += 4;
		mips.mem_write_8(p++, (byte)'l');
		mips.mem_write_8(p++, (byte)'u');
		mips.mem_write_8(p++, (byte)'a');
		mips.mem_write_8(p++, (byte)0);

		mips.set_reset_pc(e_entry);
		mips.reset();
		System.out.printf("EXEC START!\n");
		long ptime = System.currentTimeMillis();
		while(true)
		{
			//mips.run_cycles(1000000);
			mips.run_cycles(10000000);
			long ntime = System.currentTimeMillis();
			//System.err.printf("%.7f MHz\n", (1000.0/(ntime-ptime)));
			System.err.printf("%.7f MHz\n", (10000.0/(ntime-ptime)));
			ptime = ntime;
		}
	}
}

