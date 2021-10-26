class MicroDispatchBase < Benchmark
  def benchmark
    cnt = 0
    
    while cnt < 20000
      cnt = cnt + 1
    end

    cnt
  end

  def verify_result(result)
    20000 == result
  end
end
