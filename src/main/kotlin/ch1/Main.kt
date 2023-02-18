package ch1

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

object Main {
    private const val portNo = 55443
    private const val bufferSize = 2048

    @JvmStatic
    fun main(args: Array<String>) {
        // 서버 소켓을 NIO 채널로 연다
        val server = ServerSocketChannel.open()
        server.socket().bind(InetSocketAddress(portNo))
        server.socket().reuseAddress = true
        server.configureBlocking(false) // 서버 소켓채널을 넌블러킹으로 설정한다

        // 셀렉터를 만들고 OP_ACCEPT 키를 등록한다. 서버 소켓 채널에 외부 접속이 들어온 경우를 이 키를 통해 알 수 있다
        val selector = Selector.open()
        server.register(selector, SelectionKey.OP_ACCEPT)

        log("Server listening at $portNo")

        val buffer = ByteBuffer.allocate(bufferSize)
        while (true) {
            val channelCount = selector.select()  // select를 호출해 이벤트가 발생했는지 검사한다. 비동기로 설정했기 때문에 즉시 반환된다
            if (channelCount > 0) {
                val iterator = selector.selectedKeys().iterator()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    // iterator에서 remove()를 하지 않으면 소켓 처리가 정상적으로 진행되지 않는다는 점에 유의할것!
                    iterator.remove()
                    if (key.isAcceptable) {
                        // 서버에 연결이 들어와서 억셉트가 가능해진 경우
                        server.accept().run {
                            configureBlocking(false) // 서버 소켓채널을 넌블러킹으로 설정한다
                            // 셀렉터에 서버 소켓 체녈의 포트 번호에 읽기 이벤트가 발생했는지 검사하기 위해
                            // socket().port에 대해 OP_READ를 등록한다..
                            register(selector, SelectionKey.OP_READ, socket().port)
                            log("Server accept: $remoteAddress")
                        }
                    } else if (key.isReadable) {
                        // 서버에 데이터가 들어와서 읽기 가능해진 경우
                        (key.channel() as SocketChannel)!!.run {
                            if (read(buffer) >= 0) {
                                // buffer에 읽은 데이터가 들어있으므로 버퍼를 flip해서 읽은 데이터를 쓰게 만든다
                                buffer.flip()
                                log("read from $remoteAddress local $localAddress: read ${buffer.remaining()}")
                                write(buffer) // 소켓 체널에 버퍼 데이터를 기록한다
                                buffer.clear() // 버퍼 내용물을 지운다
                            } else {
                                // 데이터가 끝난 경우이므로 키를 취소하고 버퍼를 닫는다.
                                key.cancel()
                                close()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun log(s: String) {
        println(s)
    }
}