module AResetNFlop #(parameter WIDTH=1, parameter RESET_VAL=0)
   (
    input                  clk,
    input                  rst,
    input                  en,
    input [WIDTH-1:0]      d,
    output reg [WIDTH-1:0] q
    );
   
   always @(posedge clk or negedge rst) begin
      if (~rst)
        q <= RESET_VAL;
      else if (en)
        q <= d;
   end

endmodule // AResetNFlop
