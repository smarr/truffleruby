class MicroDispatch2 < Benchmark
  def benchmark
    i = 1
    cnt = 0
    
    while i <= 20000
      cnt = cnt + method(i, i)
      
      i += 1
    end
    cnt
  end

  def method(a, argument)
    argument
  end

  def verify_result(result)
    200010000 == result
  end
end
