class MicroDispatch10 < Benchmark
  def benchmark
    cnt = 0
    
    for i in 1..20000 do
      cnt = cnt + method(
          i, i, i, i, i,
          i, i, i, i, i)
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
