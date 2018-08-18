`define MAXCYCLES 3000000
`define TOHOST_ADDR 'h6000
`define SUCCESS 1

`include "BottleRocketPackage.v"
`include "MockAXI4LiteSRAM.v"

module BottleRocketPackageTB(
                             );

   integer ncycles;
   reg coreclk;
   reg corereset;
   reg TCK;
   reg TMS;
   reg TRSTn;
   reg TDI;
   wire TDO;
   reg [63:0] idcode;

`include "JTAGDTM.v"

   reg [31:0] dmi_data;
   reg [`DMI_ADDR_WIDTH-1:0] dmi_addr;
   reg [1:0]                 dmi_op;

   reg [31:0]                dmi_resp_data;
   reg [1:0]                 dmi_resp_code;

   wire          mem_awvalid;
   wire          mem_awready;
   wire [31:0]   mem_awaddr;
   wire [2:0]    mem_awprot;
   wire [3:0]    mem_awcache;
   wire          mem_wvalid;
   wire          mem_wready;
   wire [31:0]   mem_wdata;
   wire [3:0]    mem_wstrb;
   wire          mem_bvalid;
   wire          mem_bready;
   wire [1:0]    mem_bresp;
   wire          mem_arvalid;
   wire          mem_arready;
   wire [31:0]   mem_araddr;
   wire [2:0]    mem_arprot;
   wire [3:0]    mem_arcache;
   wire          mem_rvalid;
   wire          mem_rready;
   wire [1:0]    mem_rresp;
   wire [31:0]   mem_rdata;

   MockAXI4LiteSRAM mem(
		         .aclk(coreclk),
		         .aresetn(~corereset),
                         .awvalid(mem_awvalid),
                         .awready(mem_awready),
                         .awaddr(mem_awaddr),
                         .awprot(mem_awprot),
                         .awcache(mem_awcache),
                         .wvalid(mem_wvalid),
                         .wready(mem_wready),
                         .wdata(mem_wdata),
                         .wstrb(mem_wstrb),
                         .bvalid(mem_bvalid),
                         .bready(mem_bready),
                         .bresp(mem_bresp),
                         .arvalid(mem_arvalid),
                         .arready(mem_arready),
                         .araddr(mem_araddr),
                         .arprot(mem_arprot),
                         .arcache(mem_arcache),
                         .rvalid(mem_rvalid),
                         .rready(mem_rready),
                         .rresp(mem_rresp),
                         .rdata(mem_rdata)
		         );

   BottleRocketPackage br_package(.clock(coreclk),
                                  .reset(corereset),
                                  .io_clk(coreclk),
                                  .io_nmi(1'b0),
                                  .io_eip(1'b0),
                                  .io_jtag_TCK(TCK),
                                  .io_jtag_TMS(TMS),
                                  .io_jtag_TDI(TDI),
                                  .io_jtag_TDO_data(TDO),
                                  .io_jtag_reset(~TRSTn),
                                  .io_bus_aw_valid(mem_awvalid),
                                  .io_bus_aw_ready(mem_awready),
                                  .io_bus_aw_bits_addr(mem_awaddr),
                                  .io_bus_aw_bits_prot(mem_awprot),
                                  .io_bus_aw_bits_cache(mem_awcache),
                                  .io_bus_w_valid(mem_wvalid),
                                  .io_bus_w_ready(mem_wready),
                                  .io_bus_w_bits_data(mem_wdata),
                                  .io_bus_w_bits_strb(mem_wstrb),
                                  .io_bus_b_valid(mem_bvalid),
                                  .io_bus_b_ready(mem_bready),
                                  .io_bus_b_bits_resp(mem_bresp),
                                  .io_bus_ar_valid(mem_arvalid),
                                  .io_bus_ar_ready(mem_arready),
                                  .io_bus_ar_bits_addr(mem_araddr),
                                  .io_bus_ar_bits_prot(mem_arprot),
                                  .io_bus_ar_bits_cache(mem_arcache),
                                  .io_bus_r_valid(mem_rvalid),
                                  .io_bus_r_ready(mem_rready),
                                  .io_bus_r_bits_resp(mem_rresp),
                                  .io_bus_r_bits_data(mem_rdata)
                                  );

   initial begin
      $readmemh({"../spinloop/spinloop.hex"}, mem.mem);
   end


   initial begin
      coreclk = 0;
      forever #5 coreclk = ~coreclk;
   end

   initial begin
      TCK = 0;
      forever #23 TCK = ~TCK;
   end

   reg [1023:0] image_name;
   reg [31:0] image [0:32767];
   integer    image_addr;
   logic [31:0] tohost_value = 0;
   logic        breakpoint_triggered = 0;

   initial begin
      if ($value$plusargs("image=%s", image_name)) begin
         $readmemh({"test-outputs/",image_name,".hex"}, image);
         $vcdplusfile({image_name,"_jtag_test.vpd"});
      end else begin
         $info("Failure: cannot find image!\n");
         $fatal;
      end
      $vcdpluson;
      ncycles = 0;
      corereset = 1;
      repeat (20)
        @(posedge coreclk);
      corereset = 0;
      initialize_dtm();
      @(negedge TCK);
      dmi_data = 'hdeadbeef;
      dmi_addr = 'h11;
      dmi_op = 0;
      @(negedge TCK);
      write_and_read_dmi_dr(dmi_op, dmi_data, dmi_addr, dmi_resp_code, dmi_resp_data);
      @(negedge TCK);
      reset_dm();
      halt();
      // text
      for (image_addr = 'h4100; image_addr < 'h6000; image_addr = image_addr + 4)
        sba_bus_write(image_addr, image[image_addr >> 2]);
      // data
      for (image_addr = 'h7000; image_addr < 'h7100; image_addr = image_addr + 4)
        sba_bus_write(image_addr, image[image_addr >> 2]);
      write_csr(`DPC, 32'h4100);
      set_breakpoint(3'b010, `TOHOST_ADDR); // Store bp on TOHOST
      resume();
      while (!breakpoint_triggered)
         check_halted(breakpoint_triggered);
      clear_breakpoint();
      single_step();
      sba_bus_read(`TOHOST_ADDR, tohost_value);
      if (tohost_value == `SUCCESS) begin
         $info("Success!\n");
         $finish;
      end else begin
         $info("Failure: illegal value written to TOHOST!\n");
         $fatal;
      end
   end

   always @(posedge coreclk) begin
      ncycles = ncycles + 1;
      if (ncycles > `MAXCYCLES) begin
         $vcdplusclose;
         $finish;
      end
   end

endmodule // BottleRocketPackageTB
