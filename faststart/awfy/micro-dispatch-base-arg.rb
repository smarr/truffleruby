class MicroDispatchBaseArg < Benchmark
  def benchmark
    i = 1
    cnt = 0
    
    while i <= 20000
      cnt = cnt + i
      
      i += 1
    end
    cnt
  end

  def verify_result(result)
    200010000 == result
  end
end
