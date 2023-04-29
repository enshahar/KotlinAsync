# 1장. 시간처리

산업화와 함께 시간 측정과 시간을 다루는게 중요해졌고, 컴퓨터 프로그래밍도 예외는 아니다.
세계화가 진행되고 앱을 전 세계에 배포하는 경우도 많아졌고, 분야에 따라서는 아주 미세한 
시간을 다뤄야 하는 경우도 많이 있다. 

한편 사람들은 자신이 속한 지역의 지역 시간(local time)을 기준으로 생활하는데 익숙하기 때문에 
컴퓨터를 통한 서비스를 제공할 때도 고객들의 위치에 따라 해당 지역의 시간을 표시해 줘야 한다. 
하지만 세계 곳곳에 흩어져있는 고객들에 대한 정보를 모아서 저장하고 계산해야 하는 
데이터베이스나 백엔드 시스템은 지역 시간 보다는 통일된 어느 시간(보통은 UTC를 사용한다)을 
기준으로 처리를 진행하고 고객의 위치에 따라 적절한 시간대로 시간을 변환해 표시해주는 경우가 
많다. 시간대 변환은 일광절약시간(서머타임)이나 시간에 따른 국가 표준의 변화 등으로 인해 
생각처럼 단순하지 않은 경우가 있다. 단적인 예로 한국은 다음과 같은 표준시간 기준의 변화를 
겪어왔고, 이를 감안하지 않으면 과거의 시간을 처리할 때 문제가 생길 수도 있다.

- 1908년 4월 1일 동경 127.5도를 기준으로 한 UTC+08:30을 대한제국의 표준시로 지정함
- 1912년 1월 1일 일제가 표준시를 동경 135도를 기준으로 하는 일본 표준시와 같은 시간대를 식민지 조선의 표준시로 지정함
- 1954년 3월 21일 이승만 정부에서 표준시를 다시 UTC+08:30으로 환원
- 1961년 8월 10일 박정희 정부에서 표준시를 UTC+09:30으로 다시 변경

게다가 서머타임을 실시한 기간도 다양하다. 1948년~1951년, 1955년~1960년에 서머타임을 
실시하다가 중단했고, 다시 1987, 1988년 2년간 서머타임이 실시되기도 했다. 

중급 개발자가 되려면 프로그램에서 시간을 다루는 방법을 잘 이해하고 
언어에서 제공하는 시간 관련 API를 잘 쓸 줄 알아야 되며, 비즈니스 목적에 맞게 
시간을 어떻게 저장하고 표현할지에 대해 잘 알아야 한다.

코틀린의 바탕이 된 자바 언어는 초기에 잘못 설계한 달력과 시간 API로 인해 많은 사람에게 
고통을 안겨줬다. 자바 1.8에 새로운 time API가 들어가면서 그런 고통은 많이 줄어들었지만
일부 자바 개발자나 인터넷 문서가 아직도 예전의 자바 API를 사용하는 경우도 있고,
과거의 잘못된 결정(예: `java.sql.Timestamp`가 `java.util.Date`를 상속했으나,
데이터베이스 타임스탬프는 일반적인 `Date` 역할을 할 수는 없는 데이터 타입이기 때문에 
절대로 `Timestamp`를 `Date`의 하위 타입인 것처럼 생각해서는 안된다)으로 인해 
여전히 고통받는 경우도 있다.

시간과 관련해서 두가지 개념을 처리할 수 있어야 한다. 한가지는 시간이나 날짜이고 
다른 한가지는 두 시각 사이의 차이인 시간 간격(duration)이다. 

JVM 플랫폼이라면 익숙한 `java.time` 패키지에 있는 자바 time API를 
활용할 수 있겠지만, 코틀린은 다중 플랫폼 언어이기 때문에 자바 time API에만 의존할 수는 
없다. 이로 인해 코틀린은 `kotlin.time`을 통해 시간 간격에 대한 API를 제공하고, 
`kotlinx.datetime`을 통해 날짜나 시간에 대한 API를 제공한다. 

우선 조금 더 간단한 `kotlin.time`에 대해 알아보자.

## 1.1 시간 간격 처리를 위한 `kotlin.time` 패키지

`kotlin.time` 패키지의 핵심은 `Duration` 클래스다. 코틀린 표준 라이브러리 \문서(https://kotlinlang.
org/api/latest/jvm/stdlib/kotlin.time/-duration/)에 따르면 `Duration`은 
어떤 시각(instant)과 다른 시각 사이의 시간 차이를 양으로 표현해준다. `Duration`이 양수라면 두번째 
시각이 첫번째 시각 이후라는 뜻이고 음수라면 두번째 시각이 첫번째 시각 이전이라는 뜻이다.

`Duration`은 나노초 정밀도로 ±146년을 표현할 수 있다. 이보다 절대값이 더 큰 시간 간격은 
무한대(`Duration.INFINITE`)로 표현된다. 한편 `Duration.ZERO`는 `0`초를 뜻한다.

### 1.1.1 `Duration` 생성 방법

`Duration`을 생성하는 방법은 여러가지다. 우선 시간 단위를 지정해 인스턴스를 만들 수 있다. 
단위를 지정하는 함수는 `Int`, `Long`, `Double`의 확장 프로퍼티(`val`)로 선언되어 있다. 
단위 함수로는 하루, 시간, 분, 초, 밀리초, 마이크로초, 나노초를 표현하는 확장들이 있다.

- `days`: 하루(day)를 단위로 시간 간격을 얻는다. 
- `hours`: 시간(hour)을 단위로 시간 간격을 얻는다.
- `minutes`: 분(minute)을 단위로 시간 간격을 얻는다.
- `seconds`: 초(second)을 단위로 시간 각격을 얻는다.
- `milliseconds`: 밀리초(`0.001`초, 즉 `1/1_000` 초)을 단위로 시간 간격을 얻는다.
- `microseconds`: 마이크로초(`0.000_001`초, 즉 `1/1_000_000` 초)을 단위로 시간 간격을 얻는다.
- `nanoseconds`: 나노초(`0.000_000_001`초, 즉 `1/1_000_000_000` 초)을 단위로 시간 간격을 얻는다.

이런 함수를 사용하려면 `kotlin.time.Duration.Companion` 안에 있는 해당 함수를 임포트해야 한다. 
참고로, `Duration.minutes(1)` 같이 정수나 실수를 파라미터로 받는 함수도 `Duration`의 동반 객체 
안에 정의되어 있는데 해당 함수들은 모두 사용 중단 예정(deprecated)이다.

```kotlin
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val oneMinutes1 = 1.minutes        // 1m
val oneMinutes2 = 1.0.minutes      // 1m
val oneMinutes3 = 1.0005.minutes   // 1m 0.03s
val oneMinutes4 = 1.00001666666.minutes // 1m 0.001s
val oneMinutes5 = 1.0000166666.minutes  // 1m 0.000999996s
```

확장 함수의 수신 객체 타입이 정수타입이면 정확한 단위를 얻지만 실수인 경우에는 크기에 따라 가장 
가까운 **나노초나 밀리초** 단위로 반올림된다는 점(`oneMinutes4`, `oneMinutes5`)과, 
소수점으로 표현한 단위를 시분초로 변환해야 한다는 점에 유의하라. `oneMinutes4`에서 보면 
0.0005분을 초로 바꾸면 0.3초이기 때문에 결과가 "1m 0.03s"가 된다. 

한편 문자열에서 `Duration`을 얻을 수도 있다. 우선, ISO-8601 문자열 표현을 `Duration`으로 
바꾸는 방법이 있다. `Duration.parseIsoString()`은 문자열 파싱을 하다 오류가 발생하면 
예외를 던지고, `Duration.parseIsoStringOrNull()`은 오류 발생시 `null`을 반환한다.

반면, `Duration.parse()`와 `Duration.parseOrNull()`은 `Duration`의 문자열 표현 방식인 
하루/시간/분/초를 사용한 표현(예: `6d 22h 40m 40.099899999s`)을 `Duration`으로 파싱한다.

```kotlin
import kotlin.time.Duration

val s = "PT166H40M40.099899999S"
val durationFromString = Duration.parseIsoString(s) 
println(durationFromString)                      //  6d 22h 40m 40.099899999s
println(Duration.parseIsoStringOrNull(s+"XXX"))  // null
println(kotlin.time.Duration.parse("6d 22h 40m 40.099899999s")) //  6d 22h 40m 40.099899999s
```

### 1.1.2 `Duration`에 대한 산술 연산 

`Duration`에 대한 4칙연산을 제공한다. 덧셈과 뺄셈은 단순한 덧셈 뺄셈이다. 코틀린에서 `operator fun plus()`와 
`operator fun minus()`가 각각 `+`와 `-` 연산자에 대응한다는 사실을 기억하라. 앞으로는 별도로 
연산자 함수에 대해 언급하지 않을 것이다. 연산자 함수에 대해서는 다른 코틀린 기초 서적을 참고하라.

- `operator fun plus(other: Duration): Duration`
- `operator fun minus(other: Duration): Duration`

곱셉의 경우 두 시간 간격을 서로 곱하는게 의미가 없기 때문에 
주어진 정수나 실수만큼 시간 간격의 규모를 바꾸는 연산(즉 시간 간격의 정수배나 실수배)으로 정의되어 있다. 

- `operator fun times(scale: Int): Duration`
- `operator fun times(scale: Double): Duration`

나눗셈의 경우는 또 다르다. 규모를 바꾸는 연산(정수배나 실수배)과 두 시간 간격의 비율을 계산하는 연산이 
정의되어 있다. 

- `operator fun div(scale: Int): Duration`
- `operator fun div(scale: Double): Duration`
- `operator fun div(other: Duration): Double`

두 시간 간격을 비교하는 `compareTo()`와 절대값을 알아내는 프로퍼티, 단항 부호 반전(`-`) 연산도 있다.

- `fun compareTo(other: Duration): Int`
- `val absoluteValue: Duration`
- `operator fun unaryMinus(): Duration`

그 외에 시간 간격의 성질을 알아내기 위한 여러 함수가 존재한다.

- `fun isFinite(): Boolean`: 무한대가 아닌 경우 `true`
- `fun isInfinite(): Boolean`: 무한대인 경우 `true`
- `fun isNegative(): Boolean`: 음수인 경우 `true`
- `fun isPositive(): Boolean`: 양수인 경우 `true`
  
### 1.1.3 시간 단위 변환 

`Duration`을 원하는 단위로 변환하고 싶을 때는 세가지 방법이 있다.

첫번째 방법은 `inWholeXXX`라는 프로퍼티를 쓰는 방법이다. 이 경우 소수점 이하는 버려지고 
온전한 단위만 남는다는 점에 유의하라. 이 함수 역시 하루, 시간, 분, 초, 밀리초, 마이크로초, 
나노초를 지원한다.

```javascript
val days = oneMinutes5.inWholeDays                  // 0
val hours = oneMinutes5.inWholeHours                // 0
val minutes = oneMinutes5.inWholeMinutes            // 1
val seconds = oneMinutes5.inWholeSeconds            // 60
val milliseconds = oneMinutes5.inWholeMilliseconds  // 60000
val microseconds = oneMinutes5.inWholeMicroseconds  // 60000999
val nanoseconds = oneMinutes5.inWholeNanoseconds    // 60000999996
```

두번째 방법은 `toInt()`, `toLong()`과 `toDouble()` 함수에 원하는 단위를 넘기는 방식이다. 
`toInt()`나 `toLong()`을 사용하면 `inWholeXXX`와 같은 효과를 얻을 수 있고,
`toDouble()`을 사용하면 소수점 이하까지 얻을 수 있다.

```kotlin
import kotlin.time.DurationUnit

val longSeconds = oneMinutes5.toLong(DurationUnit.SECONDS)      // 60
val doubleSeconds = oneMinutes5.toDouble(DurationUnit.SECONDS)  // 60.000999996
```

단위는 `DurationUnit`에 이넘으로 정의되어 있다.

```kotlin
public expect enum class DurationUnit {
    NANOSECONDS,
    MICROSECONDS,
    MILLISECONDS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS;
}
```

세번째 방법은 `toComponents()` 함수다. 이 함수는 인자가 여럿인 람다에게 시간 값을 
분해해 전달한다. 따라서 람다는 파라미터를 통해 각 단위의 값을 얻을 수 있다. 이 함수는 
람다가 반환한 값을 반환하므로 시간 간격을 분해해 적절히 처리한 후 새로운 값을 얻고 싶을 
때 이 함수를 쓸 수 있다. 람다 파라미터의 갯수에 따라 다음과 같은 4가지 오버로딩이 존재한다.

```kotlin
fun <T> toComponents(
    action: (days: Long, hours: Int, minutes: Int, seconds: Int, nanoseconds: Int) -> T
): T

fun <T> toComponents(
    action: (hours: Long, minutes: Int, seconds: Int, nanoseconds: Int) -> T
): T

fun <T> toComponents(
    action: (minutes: Long, seconds: Int, nanoseconds: Int) -> T
): T

fun <T> toComponents(
    action: (seconds: Long, nanoseconds: Int) -> T
): T
```

예를 들면 다음과 같이 시간을 문자열로 변환할 수 있다.

```kotlin
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.microseconds

val duration = 99.minutes + 99.9999.seconds + 999999.microseconds
println(duration.toComponents { h, m, s, _ ->
    "%02d:%02d:%02d".format(h, m, s)  // 01:40:40
})
println(duration.toComponents { h, m, sec, nsec ->
    "%02d:%02d:%02d.%09d".format(h, m, sec, nsec)  // 01:40:40.999899000
})
```

### 1.1.4 문자열 반환

문자열 변환은 `toString()`을 사용한다. 아무 인자도 없는 `toString()`은 
하루(`d`), 시간(`h`),분(`m`),초(`s`) 단위로 시간을 분해해 표시해준다.
`DurationUnit`과 소수점 이하 자릿수를 파라미터로 받는 `toString()`은 
해당 단위에 맞춰 시간을 변환해 표시해준다.


```kotlin
// import 생략
val duration2 = 9999.minutes + 99.9999.seconds + 99999999.nanoseconds
println(duration2.toString())  // 6d 22h 40m 40.099899999s
println(duration2.toString(DurationUnit.DAYS,10)) // 6.9449085637d
println(duration2.toString(DurationUnit.HOURS,2)) // 166.68h
```

`toIsoString()`을 사용하면 ISO-8601 표준에 맞는 문자열 표현을 얻을 수 있다.

```kotlin
println(duration2.toISOString()) // PT166H40M40.099899999S
```

### 1.1.5 시간 간격의 범위

두가지 시간 간격을 사용해 범위를 만들 수도 있다. 두번째 `Duration`이 포함되는 
`ClosedRange`와 두번째 `Duration`이 제외되는 `OpenEndRange`를 만들어낼 수 있다.

```kotlin
val range1 = Duration.ZERO .. duration2

@kotlin.ExperimentalStdlibApi
val range2 = Duration.ZERO ..< duration2
```

>##### 노트
> `operator fun rangeTo()`가 `..` 연산자를 정의하듯, 
> `operator fun rangeUntil()`이 `..<` 연산자를 정의한다.
> 하지만 아직 `..<` 연산은 실험 단계이며, 코틀린 1.72에 처음 소개됐기 때문에,
> 코틀린 1.72 버전 이후부터 사용가능하며 아직(1.8 현재)은 `@kotlin.ExperimentalStdlibApi` 애너테이션을 
> 붙여서 사용해야만 한다.