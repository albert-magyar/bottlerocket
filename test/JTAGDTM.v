// JTAG DTM constants
`define DMI_DR_WIDTH 41
`define DMI_ADDR_WIDTH 7
`define IR_WIDTH 5
`define DTMCS 'h10
`define DMI 'h11
`define DMI_NOP_OP 0
`define DMI_READ_OP 1
`define DMI_WRITE_OP 2
`define DMI_SUCCESS 0

// DMI constants
`define DMCONTROL 'h10
`define DMSTATUS 'h11
`define COMMAND 'h17
`define DATA0 'h04
`define SBCS 'h38
`define SBADDRESS0 'h39
`define SBDATA0 'h3c

// CSRs
`define DCSR 'h7b0
`define DPC 'h7b1
`define TSELECT 'h7a0
`define TDATA1 'h7a1
`define TDATA2 'h7a2

task automatic pause;
   begin
      repeat (10)
        @(negedge TCK);
   end
endtask

task automatic tap_ns;
   input val;
   begin
      TMS = val;
      @(negedge TCK);
   end
endtask

// Starts from any state
// Ends in Run-Test/Idle
task automatic reset_tap;
   begin
      @(negedge TCK);
      TMS = 1'b1;
      TRSTn = 1'b0;
      repeat (10)
        @(negedge TCK);
      TRSTn = 1'b1;
      @(negedge TCK);
      tap_ns(1'b0); // Run-Test/Idle
   end
endtask // repeat

// Starts from Shift
// Ends in Exit1
task automatic shift_value;
   output [`DMI_DR_WIDTH-1:0] out_value;
   input  [`DMI_DR_WIDTH-1:0] in_value;
   input integer              shift_width;
   automatic integer          count;
   automatic reg [`DMI_DR_WIDTH-1:0] sr;
   begin
      count = 1;
      sr = in_value;
      TMS = 1'b0;
      TDI = sr[0];
      while (count < shift_width) begin
         @(posedge TCK);
         sr = {TDO, sr[`DMI_DR_WIDTH-1:1]};
         @(negedge TCK);
         TDI = sr[0];
         count = count + 1;
      end
      TMS = 1'b1;
      @(posedge TCK);
      sr = {TDO, sr[`DMI_DR_WIDTH-1:1]};  
      @(negedge TCK);
      out_value = sr >> (`DMI_DR_WIDTH - shift_width);
   end
endtask // while

// Starts from Run-Test/Idle
// Ends in Run-Test/Idle
task automatic read_idcode;
   automatic reg [`DMI_DR_WIDTH-1:0] tmp;
   begin
      tap_ns(1'b1); // Select-DR Scan
      tap_ns(1'b0); // Capture-DR
      tap_ns(1'b0); // Shift-DR
      shift_value(tmp, 0, 32); // Ends in Exit1-DR
      tap_ns(1'b1); // Update-DR
      tap_ns(1'b0); // Run-Test/Idle
      idcode = tmp;
   end
endtask // tap_ns

// Starts from Run-Test/Idle
// Ends in Run-Test/Idle
task automatic write_ir;
   input reg [`IR_WIDTH-1:0] ir_value;
   automatic reg [`IR_WIDTH-1:0] tmp;
   begin
      tap_ns(1'b1); // Select-DR Scan
      tap_ns(1'b1); // Select-IR Scan
      tap_ns(1'b0); // Capture-IR
      tap_ns(1'b0); // Shift-IR
      shift_value(tmp, ir_value, `IR_WIDTH); // Ends in Exit1-IR
      tap_ns(1'b1); // Update-IR
      tap_ns(1'b0); // Run-Test/Idle
   end
endtask


// Starts from Run-Test/Idle
// Ends in Run-Test/Idle
task automatic initialize_dr;
   input reg [`DMI_DR_WIDTH-1:0] dr_value;
   input integer dr_width;
   automatic reg [`DMI_DR_WIDTH-1:0] tmp;
   begin
      tap_ns(1'b1); // Select-DR Scan
      tap_ns(1'b0); // Capture-DR
      tap_ns(1'b0); // Shift-DR
      shift_value(tmp, dr_value, dr_width); // Ends in Exit1-DR
      tap_ns(1'b1); // Update-DR
      tap_ns(1'b0); // Run-Test/Idle
   end
endtask // tap_ns

// Starts from Run-Test/Idle
// Ends in Pause-DR with DMI in IR
task automatic initialize_dtm;
   begin
      reset_tap(); // Ends in Run-Test/Idle
      read_idcode();
      write_ir(`DTMCS); 
      initialize_dr(32'hFFFFFFFF, 32);
      write_ir(`DMI); // Ends in Run-Test/Idle
      tap_ns(1'b1); // Select-DR Scan
      tap_ns(1'b0); // Capture-DR
      tap_ns(1'b0); // Shift-DR
      tap_ns(1'b1); // Exit1-DR
      tap_ns(1'b0); // Pause-DR
   end
endtask

// Starts from Pause-DR
// Ends in Pause-DR
task automatic write_and_read_dmi_dr;
   input  [1:0]                 op;
   input  [31:0]                data;
   input  [`DMI_ADDR_WIDTH-1:0] addr;
   output [1:0]                 resp_op;
   output [31:0]                resp_data;
   automatic reg [`DMI_DR_WIDTH-1:0] tmp;
   begin
      tap_ns(1'b1); // Exit2-DR
      tap_ns(1'b0); // Shift-DR
      shift_value(tmp, {addr, data, op}, `DMI_DR_WIDTH); // Ends in Exit1-DR
      tap_ns(1'b0); // Pause-DR
      tap_ns(1'b1); // Exit2-DR
      tap_ns(1'b1); // Update-DR
      tap_ns(1'b0); // Run-Test/Idle
      tap_ns(1'b0); // Run-Test/Idle
      tap_ns(1'b0); // Run-Test/Idle
      tap_ns(1'b0); // Run-Test/Idle
      tap_ns(1'b0); // Run-Test/Idle
      tap_ns(1'b1); // Select-DR Scan
      tap_ns(1'b0); // Capture-DR
      tap_ns(1'b0); // Shift-DR
      shift_value(tmp, 0, `DMI_DR_WIDTH); // Ends in Exit1-DR
      tap_ns(1'b0); // Pause-DR
      resp_op = tmp[1:0];
      resp_data = tmp[33:2];
      if (resp_op != `DMI_SUCCESS) begin
         $info("Failure: DMI access failed!\n");
         $fatal;   
      end
   end
endtask

task automatic reset_dm;
   automatic reg [1:0] resp_op;
   automatic reg [31:0] resp_data;
   begin
      write_and_read_dmi_dr(`DMI_WRITE_OP, 32'b0, `DMCONTROL, resp_op, resp_data);
      write_and_read_dmi_dr(`DMI_WRITE_OP, 32'b1, `DMCONTROL, resp_op, resp_data);
   end
endtask

task automatic read_dmi_reg;
   input [`DMI_ADDR_WIDTH-1:0] addr;
   output [31:0]                rdata;
   automatic reg [1:0] resp_op;
   automatic reg [31:0] resp_data;
   begin
      write_and_read_dmi_dr(`DMI_READ_OP, 0, addr, resp_op, resp_data);
      rdata = resp_data;
   end
endtask

task automatic write_dmi_reg;
   input [`DMI_ADDR_WIDTH-1:0] addr;
   input [31:0]                wdata;
   automatic reg [1:0] resp_op;
   automatic reg [31:0] resp_data;
   begin
      write_and_read_dmi_dr(`DMI_WRITE_OP, wdata, addr, resp_op, resp_data);
   end
endtask

task automatic halt;
   automatic reg [31:0] dmstatus;
   begin
      write_dmi_reg(`DMCONTROL, 32'h80000001);
      read_dmi_reg(`DMSTATUS, dmstatus);
      if (!dmstatus[9]) begin
         $info("Failure: halt unsuccessful!\n");
         $fatal;
      end
   end
endtask

task automatic check_halted;
   output halted;
   automatic reg [31:0] dmstatus;
   begin
      read_dmi_reg(`DMSTATUS, dmstatus);
      halted = dmstatus[9];
   end
endtask

task automatic resume;
   automatic reg [31:0] dmstatus;
   begin
      write_dmi_reg(`DMCONTROL, 32'h40000001);
      read_dmi_reg(`DMSTATUS, dmstatus);
      if (!dmstatus[17]) begin
         $info("Failure: resume unsuccessful!\n");
         $fatal;
      end
   end
endtask

task automatic sba_bus_write;
   input [31:0] addr;
   input [31:0] wdata;
   begin
      write_dmi_reg(`SBADDRESS0, addr);
      write_dmi_reg(`SBDATA0, wdata);
   end
endtask

task automatic sba_bus_read;
   input [31:0] addr;
   output [31:0] rdata;
   automatic reg [31:0] tmp;
   begin
      write_dmi_reg(`SBADDRESS0, addr);
      write_dmi_reg(`SBCS, 32'h140000);
      read_dmi_reg(`SBDATA0, tmp);
      rdata = tmp;
   end
endtask

task automatic write_any_reg;
   input [15:0] addr;
   input [31:0] wdata;
   begin
      write_dmi_reg(`DATA0, wdata);
      write_dmi_reg(`COMMAND, {16'h0023, addr});
   end
endtask // write_dmi_reg

task automatic read_any_reg;
   input [15:0] addr;
   output [31:0] rdata;
   begin
      write_dmi_reg(`COMMAND, {16'h0022, addr});
      read_dmi_reg(`DATA0, rdata);
   end
endtask

task automatic write_csr;
   input [11:0] addr;
   input [31:0] wdata;
   begin
      write_any_reg({4'h0, addr}, wdata);
   end
endtask

task automatic read_csr;
   input [11:0] addr;
   output [31:0] rdata;
   begin
      read_any_reg({4'h0, addr}, rdata);
   end
endtask

task automatic write_gpr;
   input [4:0] addr;
   input [31:0] wdata;
   begin
      write_any_reg({11'h200, addr}, wdata);
   end
endtask

task automatic read_gpr;
   input [4:0] addr;
   output [31:0] rdata;
   begin
      read_any_reg({11'h200, addr}, rdata);
   end
endtask

task automatic set_breakpoint;
   input [2:0] exec_store_load_mask;
   input [31:0] addr;
   begin
      write_csr(`TSELECT, 0);
      write_csr(`TDATA2, addr);
      write_csr(`TDATA1, 32'h08001078 | exec_store_load_mask);
   end
endtask

task automatic clear_breakpoint;
   begin
      write_csr(`TSELECT, 0);
      write_csr(`TDATA1, 32'h08001078);
   end
endtask

task automatic single_step;
   automatic reg [31:0] tmp; 
   begin
      read_csr(`DCSR, tmp);
      write_csr(`DCSR, tmp | 'h4);
      resume();
      read_dmi_reg(`DMSTATUS, tmp);
      if (!tmp[9] || !tmp[17]) begin
         $info("Failure: single-step unsuccessful!\n");
         $fatal;
      end
   end
endtask

