module AResetNFlop #(parameter WIDTH=1, parameter RESET_VAL=0)
   (
    input                  clk,
    input                  resetn,
    input [WIDTH-1:0]      D,
    output reg [WIDTH-1:0] Q
    );
   
   always @(posedge clk or negedge resetn) begin
      if (~resetn)
        Q <= RESET_VAL;
      else 
        Q <= D;
   end

endmodule // AResetNFlop
