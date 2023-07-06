import chisel3._
import chisel3.util._


object util {
    def detectRisingEdge(x: Bool) = x && !RegNext(x)
}

object conf {
    val delay1ms = 1000000
}

class TFF extends Module{
    val io = IO(new Bundle{
        val out = Output(Bool())
        val enable = Input(Bool())
    })

    io.out := ~RegEnable(io.out, 0.U(1.W), io.enable)
}

class Counter extends Module{
    val io = IO(new Bundle{
        val enable = Input(Bool())
        val count = Output(UInt(log2Ceil(conf.delay1ms).W))
    })

    val counter = RegInit(0.U(log2Ceil(conf.delay1ms).W))
    val count_cases = Seq(
        ~io.enable                                -> 0.U,
        (io.enable & (counter < conf.delay1ms.U))   -> (counter + 1.U),
        (io.enable & (counter === conf.delay1ms.U)) -> counter
    )
    counter := MuxCase(0.U, count_cases)
    io.count := counter
}

class Debouncer extends Module{
    val io = IO(new Bundle{
        val toDebounce = Input(Bool())
        val debounced = Output(Bool())
    })

    val my_counter = Module(new Counter)

    my_counter.io.enable    := io.toDebounce
    io.debounced            := my_counter.io.count === conf.delay1ms.U
}

class ButtonPushed extends Module{
    val io = IO(new Bundle{
        val button = Input(Bool())
        val pushed = Output(Bool())
    })

    val myDebouncer = Module(new Debouncer)
    myDebouncer.io.toDebounce := io.button
    io.pushed                 := util.detectRisingEdge(myDebouncer.io.debounced)
}

class StateToggler extends Module{
    val io = IO(new Bundle{
        val button = Input(Bool())
        val state = Output(Bool())
    })
    val myTff        = Module(new TFF)
    val buttonPushed = Module(new ButtonPushed) 
    buttonPushed.io.button := io.button
    myTff.io.enable        := buttonPushed.io.pushed
    io.state               := myTff.io.out
}

class ShiftLeds extends Module{
    val io = IO(new Bundle{
        val in = Input(Bool())
        val en = Input(Bool())
        val leds = Output(UInt(24.W))
    })

    val my_shift = ShiftRegisters(io.in, 24, 0.U(1.W), io.en)
    io.leds := Cat(my_shift)
}

class AlchitryCUTop extends Module {
    val io = IO(new Bundle{
        val stateButton = Input(Bool())
        val shiftButton = Input(Bool())
        val leds = Output(UInt(24.W))
    })
    // the alchitry CU board has an active low reset
    val reset_n = !reset.asBool

    withReset(reset_n){
        val stateButton = Module(new StateToggler)
        val shiftButton = Module(new ButtonPushed)
        val shiftedLeds = Module(new ShiftLeds)
        
        stateButton.io.button := ~io.stateButton
        shiftButton.io.button := ~io.shiftButton

        shiftedLeds.io.en := shiftButton.io.pushed
        shiftedLeds.io.in := stateButton.io.state
        io.leds := shiftedLeds.io.leds
        }
}

object Main extends App{
    (new chisel3.stage.ChiselStage).emitVerilog(new AlchitryCUTop, Array("--target-dir", "build/artifacts/netlist/"))
}