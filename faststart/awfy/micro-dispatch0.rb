class MicroDispatch0 < Benchmark
  def benchmark
    i = 1
    cnt = 0
    
    while i <= 20000
      cnt = cnt + method
      
      i += 1
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
