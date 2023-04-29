# 1장. 여러 비동기 처리 방식 비교

1장에서는 여러 비동기 처리 방식을 비교해본다. 각각의 방식을 자세히 다루기에는 지면이 부족하므로,
각 방식의 핵심 아이디어와 장단점, 간단한 예제를 살펴본다.

## 1.1 바쁜 대기(busy waiting)나 폴링(polling)

비동기 처리 방법을 나열하라고 하면 보통 사람들이 잘 언급하지 않는 방법이 바로 바쁜대기(busy waiting)를 활용해 
비동기 이벤트를 감지하고 처리하는 방법이다. 

전통적인 예제로 소켓 프로그래밍에 사용하는 `select` 함수를 들 수 있다. `select`는 여러가지 소켓 연결이 존재할 때 
그 중 입력이나 이벤트가 발생한 소켓들을 골라낼 수 있는 함수다. 여러 입력 중 몇가지를 골라낼 수 있기 때문에 이를 멀티플렉서(multiplexer)라고 부르기도 한다. 
`select`는 원하는 이벤트가 발생한 소켓이 있는 경우 양수를, 이벤트가 발생한 소켓이 없는 경우 0을, 오류가 발생한 경우 음수를 반환한다.

한편 `select`를 블러킹 모드로 사용할 수도 있고 즉시 반환되는 모드로 사용할 수도 있다. 
`select`를 호출할 때 타임아웃을 지정하면 함수가 블럭된다. 타임아웃으로 지정한 시간 안에 원하는 이벤트(소켓 연결, 데이터 송수신 등)가 
발생하지 않으면 `0`이 반환되면서 아무 이벤트도 없었고 타임아웃에 도달했음을 알려준다. 하지만 `select`를 호출하면서 
타임아웃을 0으로 지정하면 `select`가 블럭되지 않고 즉시 반환되기 때문에, 호출자를 블럭시키지 않는다. 이 성질을 이용해 
소켓 이벤트를 주기적으로 폴링하거나 이벤트가 발생하는지 체크하면서 무한루프를 도는 바쁜 대기를 수행할 수 있다.

코틀린에는 소켓 라이브러리가 없지만 자바 NIO는 전통적인 `select` 방식과 똑같은 방식으로 사용할 수 있는 
`Selector`를 제공한다(https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/nio/channels/Selector.html)
여기서는 간단하게 소켓 연결을 통해 들어오는 입력을 그대로 되돌려주는 에코(echo) 서버 코드를 코틀린으로 작성해 살펴보자.

>##### 코드 1.1 NIO 셀렉터를 사용한 에코 서버


```kotlin
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
            val channelCount = selector.select()  // select를 호출해 이벤트가 발생했는지 검사한다. 비동기로 설정했ㄱ
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
```

이 코드에서 비동기 처리에 있어 중요한 부분은 바로 다음 부분이다.

```kotlin
        while (true) {
            val channelCount = selector.select()  // 1
            if (channelCount > 0) { // 2
                // 3
            }
        }
```


1. 이벤트가 도착했는지 검사하고 *즉시* 반환되는 함수를 호출한다.
2. 1에서 함수를 호출한 결과를 보고 이벤트 발생 여부를 판정한다.
3. 이벤트가 발생한 경우 이 위치에서 필요한 처리를 진행한다.

이런 식으로 코드를 작성하면 콜백이나 별다른 장치 없이도 이벤트 발생을 감지하고 처리할 수 있다.
운영체제 내에서 메모리 매핑된 I/O 상태를 검사하거나, 펌웨어에서 장치 상태를 감시해 처리할 때 이런식의 코드를 사용할 수  있다.

하지만 이 방식은 한계가 뚜렸하다.

1. 타이머를 사용한 폴링이라면 모르겠지만, 바쁜 대기는 CPU를 놔주지 않기 때문에 CPU 활용도가 낮아진다. 
2. 확장성이 떨어진다. 관찰해야 할 이벤트를 추가해야 하거나, 이벤트 별 우선순위를 조절해야 하거나, 이벤트를 조합해 새 이벤트를 정의해야 하는 경우 등의 다양한 경우를 대응하기 어렵다.
3. 오류 처리가 쉽지 않다.
3. 테스트가 쉽지 않다.

조금만 생각해보면 프로그램 메인 로직에서 이벤트를 일일이 검사하는 대신 이벤트 발생시 통지를 받을 수 있다면 좀 더 편리할 것 같다. 바로 이런 아이디어로 나온게 이제부터 설명할 콜백 패턴이다.

## 1.2 콜백 패턴

메인 로직은 할일을 하고 이벤트 발생시 통지를 받는다는 개념은 일상생활(전화, 문자, 비서를 통한 간접 처리 등)이나 하드웨어/운영체제(인터럽트 처리) 등에서도 이미 널리 쓰이고 
있고 이해하기 쉬운 패턴이다. 전화번호를 남기면 나중에 상담원이 전화를 걸어서 필요한 일을 처리해주는 
스타일의 콜센터와 상담원이 응답할 때까지 기다려야 하는 콜센터가 있다면 전자쪽이 더 편리할 것이다. 이렇게 상담원이 
전화를 다시 걸어주는 *콜백(callback)*을 소프트웨어에 적용한 패턴이 콜백이다. 다른 형태로 리스너(listener)를 정의해 
등록해두면 나중에 리스너의 메서드를 호출해주는 형태도 역시 콜백 패턴에 속한다.

콜백 패턴의 문제로 가장 널리 알려진 것은 콜백지옥이다. 다음 자바 스크립트 코드를 살펴보자.

```javascript
function fn() {
    setTimeout(() => {
        console.log('타임아웃1');
        setTimeout(() => {
            console.log('타임아웃2');
            setTimeout(() => {
                console.log('타임아웃3');
            }, 0);
        }, 0);
    }, 0);
}

fn();
```

자바스크립트 `setTimeout`은 전달받은 람다를 지정된 시간 이후 호출해준다. 여기서는 시간으로 `0`을 지정했기 때문에 
즉시 콜백 호출이 이뤄진다. 이때 콜백 호출을 `setTimeout()` 함수 내부에서 해주지 않고 자바스크립트 런타임이 비동기적으로 
수행한다는 점에 유의하라. 가장 바깥쪽 타이머의 콜백 람다가 불리면, 이 람다는 "타임아웃1"을 콘솔에 출력하고(`console.log`) 
다시 타이머를 설정한다. 이때 전달된 람다가 즉시 호출되면서 "타임아웃2"를 찍고 다시 세번째 타임아웃을 설정한다. 이 마지막 
타임아웃에 전달된 람다는 콘솔에 "타임아웃3"를 찍고, 프로그램이 끝난다. 한가지 좋은 점은 콜백을 등록하고 
호출될 때까지 메인 로직은 할일이 없기 때문에 CPU를 다른 처리에 양보할 수 있다는 점이다. 자바스크립트는 메인 스레드에서 
모든 일을 처리하는 엔진이었지만 콜백을 활용한 비동기 처리를 통해 웹페이지 DOM 제어와 이벤트 처리를 모두 수행할 수 있다.
다만, 스레드가 하나뿐이기 때문에 중간에 무한루프가 있거나 하면 모든 처리가 블럭된다는 문제도 있다.
  
특히 순차적으로 요청이 이뤄져야 하는 경우 어쩔 수 없이 콜백 안에서 다른 함수를 호출하면서 콜백을 설정해야 하기 때문에 
콜백을 내포시키는 수 밖에 없는 경우가 많다. 이렇게 콜백을 내포시키다 보면 점차 들여쓰기가 깊어지고 코드에서 콜백이 하는 일을 알아보기 힘들어진다. 
그래서 이를 **콜백 지옥(callback hell)**이라 부른다. 하지만 콜백 지옥은 단지 내포된 콜백의 깊이가 커지면서 코드 가독성이 
낮아진다는 이유만으로 문제인 것은 아니다. 코드 복잡도가 늘어나고 프로그램상의 내포 구조가 코드 실행 순서와 
같지 않기 때문에 코드를 이해하기 어려워지는게 더 큰 문제다. 

콜백 스타일의 코드도 역시 한계가 분명하다.

1. 순차적 연결이나 합성, 확장이 쉽지 않다. 콜백을 순서대로 연결해야 하는 경우 앞에서 본 콜백 지옥이 발생함은 물론, 한 콜백의 결과와 다른 콜백의 결과를 합치거나, 콜백의 결과를 변환하고 싶거나 할때 콜백 내부에서 이를 처리하기가 간단하지 않다.
2. 오류 처리가 쉽지 않다.
3. 테스트가 어렵다.

어떻게 이를 개선할 수 있을까? 콜백을 제공하는 대신, 필요할 때 비동기 이벤트가 발생했는지 여부를 체크하고 
결과를 얻을 수 있는 어떤 장치를 돌려받을 수 있다면, 나중에 원하는 시점에 그 장치를 통해 비동기 이벤트 발생 여부를 
검사하고 결과 데이터를 처리할 수 있다. 이런 역할을 하는 객체를 다양한 이름으로 부를 수 있겠지만,
보통은 퓨처(`Future`)나 프라미스(`Promise`)라고 부른다.

## 1.3 퓨처와 프라미스

언어에 따라 약간씩 차이는 있지만, 퓨처는 보통 "미래에 완료될 어떤 연산의 결괏 값"이 담긴 객체를, 프라미스는 "어떤 값을 대입하면 
퓨처를 완료시킬 수 있는" 객체를 뜻한다. 둘을 구분하지 않는 언어도 많은데, 이런 언어에서는 퓨처나 프라미스 중 한가지 이름을 사용한다. 

예를 들어 자바스크립트는 `Promise`를 사용해 비동기적으로 완료될 수 있는 값을 표현할 수 있다. 
앞에서 본 `setTimeout()` 함수 대신 미래에 비동기적으로 완료되는 프라미스를 돌려주는 함수 `delay()`를 사용하면 
타이머와 비슷한 처리를 할 수 있다.

```javascript
function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

var x = delay(1000) // 1초 후 완료

x.then(()=>alert("done!")) // 완료시 ()=>alert("done!")을 실행함
```

여기서 `then()`은 콜백을 지정하는 함수처럼 보인다. 따라서 이런 접근 방법이 콜백을 사용한 접근 방법과 
큰 차이가 없다고 생각할 수도 있다. 하지만 프라미스는 객체이기 때문에 자유롭게 
여기저기 전달이 될 수 있고 원하는 지점에 `then()`을 써서 콜백을 실행하게 지정할 수 있지만 
`setTimeout()`은 타이머를 설정하는 지점에서 콜백을 연결해야만 하기 때문에 훨씬 더 자유도가 떨어진다.

또, 내포된 콜백으로 처리해야만 했던 연속적인 비동기 호출이나, 플래그 등을 사용해 모든 이벤트가 처리됐는지 검사해야 했던 
병렬 비동기 호출 등의 경우도 프라미스를 활용하면 훨씬 자연스럽게 해결된다.

```javascript
delay(0).then( () => {
    console.log("타임아웃1")
    delay(0)                  // 1
}).then( () => {              // 2
    console.log("타임아웃2")
    delay(0)
}).then( () => {
    console.log("타임아웃3")
})
```

1. `then()`에 전달한 람다의 맨 마지막에 있는 값이 람다의 반환값이고, `then()`은 이 람다의 반환값을 돌려준다. 여기서는 `delay(0)`를 호출해서 만든 새 프라미스를 반환한다.
2. `then()`이 1에서 정의한 프라미스를 반환하기 때문에 `then()`을 연쇄적으로 사용할 수 있다.

>##### 자바 1.5 `Future`의 문제점
> 자바의 경우 자바 1.5에 동시성 패키지(`java.util.concurrent`)를 도입하면서 생긴 `Future` 클래스가 본 절 초반에 설명한 
퓨처에 해당한다. 하지만 자바 `Future` 설계에는 몇가지 아쉬운 점이 있다. 
> 
> - 퓨처를 `cancel()`로 취소하거나 결과를 `get()`으로 가져올 수는 있지만, 결과를 받아 처리하는 콜백을 등록(또는 다른 관점에서 보면 결과를 매핑(mapping))할 수 없다.
> - 퓨처를 수동으로 완료시킬 방법이 없다.
> - 예외 처리를 제공하지 않는다.
> - 퓨처와 퓨처를 서로 조합하는 연산을 제공하지 않는다.
> 
> 자바 8에 도입된 `CompletableFuture`, `CompletionStage` 등은 이런 제약을 해결해 준다.
> 자바 `CompletableFuture`의 완료 상태에는 "예외로 완료(completed exceptionally)"가 있어서 예외 처리도 가능하다.

`CompletableFuture`를 사용해 앞에서 본 `delay()`를 코틀린으로 다시 구현하고, 모든 퓨처가 완료됐을 때 
완료되는 퓨처를 돌려주는 `allOf()`와 퓨처 중 어느 하나가 완료되면 완료되는 퓨처를 돌려주는 `anyOf()`를 사용한 
예제를 보면 다음과 같다.

```kotlin
package ch1

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

internal object CompletableFutureExample {
    fun <T> delay(ms: Int, block: Supplier<T>): CompletableFuture<T> =
        CompletableFuture<T>()
            .completeAsync( block,                                                        // 1
                CompletableFuture.delayedExecutor(ms.toLong(), TimeUnit.MILLISECONDS)     // 2
            )

    @JvmStatic
    fun main(args: Array<String>) {
        val future1 = delay(1000) { println("Future1 done.");1 }
        val future2 = delay(2000) { println("Future2 done.");2 }
        val future3 = delay(3000) { println("Future3 done.");3 }
        
        future1.thenAcceptAsync { println("future1: $it") }
        future2.thenAcceptAsync { println("future2: $it") }
        future3.thenAcceptAsync { println("future3: $it") }

        val all = CompletableFuture.allOf(future1, future2, future3)  // 3
        all.thenAcceptAsync { println("all DONE!!!!") }

        val any = CompletableFuture.anyOf(future1, future2, future3)  // 4
        any.thenAcceptAsync { println("any DONE!!!!") }

        println("Future setup end")

        Thread.sleep(5000)                                            // 5
        println("Main thread end")
    }
}
```

1. `CompletableFuture`의 `completeAsync()`는 비동기적으로 지정한 실행기(executor)에 의해 완료되는 `CompletableFuture` 객체를 생성한다.
2. `delayedExecutor()`는 지정한 시간 이후 주어진 작업을 수행하는 실행기를 만들어낸다. 이 실행기를 `completeAsync()`의 두번째 인자로 넘기면 지정한 시간 이후 비동기적으로 완료되는 `CompletableFuture`가 생긴다.
3. `allOf`는 `future1`, `future2`, `future3`가 모두 완료된 경우에만 완료되는 `CompletableFuture`를 돌려준다. 세 퓨처 중 가장 나중에 완료되는 것이 `future3`이기 때문에, `all` 퓨처는 3초후 완료된다.
4. `anyOf`는 `future1`, `future2`, `future3`중 어느 하나라도 완료된 경우에만 완료되는 `CompletableFuture`를 돌려준다. 세 퓨처 중 가장 먼저 완료되는 것이 `future1`이기 때문에, `any` 퓨처는 1초후 완료된다.
5. 세 퓨처가 모드 끝날 때까지 메인 스레드를 `sleep()` 시킨다. 이 `sleep()`이 없으면 프로그램이 바로 종료되기 때문에 퓨처가 완료되는 모습을 관찰할 수 없다.


퓨처나 프라미스는 유용하지만 몇가지 단점이 있다.

- 콜백에 비해 훨씬 낫지만 아직 손쉽게 조합해 사용하기는 어렵다.
- 퓨처의 `get()` 메서드를 사용하면 스레드가 블럭된다.
- 값을 하나만 내놓을 수 있다. 비동기로 여러번 이벤트가 발생하는 경우를 퓨처로 표현하기 어렵다.

퓨처나 프라미스의 문제점을 해결하려는 시도를 몇가지 방향으로 나눌 수 있다.

첫째로 퓨처등을 사용한 연산의 합성성을 높일 수 있는 함수들을 추가함으로써 좀 더 쉽게 
퓨처와 프라미스를 서로 합성해 사용할 수 있게 지원하는 것이다. 특히 함수형 프로그래밍 기법을 
활용하면 퓨처에 대한 다양한 연산을 만들고 그런 연산의 함성성을 
활용해 조금 더 쉽게 퓨처가 포함된 연산을 서로 함성해 활용할 수 있다. 
이런 아이디어의 핵심에는 모나드(monad), 프리모나드(free monad) 등의 개념이 자리잡고 있다. 
이에 대해서는 나중에 함수형 프로그래밍에 대해 다룰 때 간단히 소개한다.

둘째로, 비동기 처리가 필요한 코드를 일반적인 코드를 작성하듯이 기술할 수 있게 도와주는 
언어 기능을 추가하는 방식이 있다. 최근 다양한 언어에 도입된 `async`/`await`이나 
코루틴 등이 이런 방식이다. 1.4절에서는 코틀린에서 지원하는 비동기 기법인 코루틴에 대해 
소개한다.

세째로, 비동기 처리를 해결하는 관점을 아예 바꾸는 방법이 있다. 동기식 처리나 퓨처를 사용한 
비동기 처리는 모두 

## 1.4 반응형 스트림

