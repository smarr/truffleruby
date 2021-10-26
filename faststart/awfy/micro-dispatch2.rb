class MicroDispatch2 < Benchmark
  def benchmark
    cnt = 0
    
    for i in 1..20000 do
      cnt = cnt + method(i, i)
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
