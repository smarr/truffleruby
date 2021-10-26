class MicroDispatchBaseArg < Benchmark
  def benchmark
    cnt = 0
    
    for i in 1..20000 do
      cnt = cnt + i
    end
    cnt
  end

  def verify_result(result)
    200010000 == result
  end
end
