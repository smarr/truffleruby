class MicroDispatchBase < Benchmark
  def benchmark
    cnt = 0
    
    for i in 1..20000 do
      cnt = cnt + 1
    end
    cnt
  end

  def verify_result(result)
    20000 == result
  end
end
