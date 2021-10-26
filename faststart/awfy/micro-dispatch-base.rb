class MicroDispatchBase < Benchmark
  def benchmark
    i = 1
    cnt = 0
    
    while i <= 20000
      cnt = cnt + 1
      
      i += 1
    end

    cnt
  end

  def verify_result(result)
    20000 == result
  end
end
