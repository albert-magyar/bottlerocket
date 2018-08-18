module AsyncResetReg(
                     input      rst,
                     input      clk,
                     input      en,
                     input      d,
                     output reg q
                     );

   always @(posedge clk or posedge rst) begin
      if (rst)
        q <= 1'b0;
      else if (en)
        q <= d;
   end
endmodule // AsyncResetReg
