class MicroDispatch0 < Benchmark
  def benchmark
    cnt = 0
    
    while cnt < 20000
      cnt = cnt + method
    end
    cnt
  end

  def method()
    1
  end

  def verify_result(result)
    20000 == result
  end
end
