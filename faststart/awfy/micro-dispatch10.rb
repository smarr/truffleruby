class MicroDispatch10 < Benchmark
  def benchmark
    i = 1
    cnt = 0
    
    while i <= 20000
      cnt = cnt + method(
          i, i, i, i, i,
          i, i, i, i, i)
      
      i += 1
    end
    cnt
  end

  def method(a1, a2, a3, a4, a5, a6, a7, a8, a9, argument)
    argument
  end

  def verify_result(result)
    200010000 == result
  end
end
