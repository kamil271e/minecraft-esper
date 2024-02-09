# Charakterystyka danych 

*Wyposażeni w odpowiednie narzędzia schodzimy pod ziemię, na odpowiednią głębokość, w celu znalezienia drogocennych rud występujących w grupach o różnym rozmiarze.*

W strumieniu pojawiają się zdarzenia zgodne ze schematem `MinecraftEvent`.

```
create json schema MinecraftEvent(ore string, depth int, number int, ets string, its string);
```

Każde zdarzenie związane z jest z faktem wydobycia określonego surowca o określonym rozmiarze z określonej głębokości. 

Dane uzupełnione są o dwie etykiety czasowe zaokrąglone do jednej sekundy. 
* Pierwsza (`ets`) związana jest z momentem wydobycia surowca. 
  Etykieta ta może się losowo spóźniać w stosunku do czasu systemowego maksymalnie do 60 sekund.
* Druga (`its`) związana jest z momentem rejestracji zdarzenia w systemie.

# Opis atrybutów

Atrybuty w każdym zdarzeniu zgodnym ze schematem `MinecraftEvent` mają następujące znaczenie:

* `ore` - rodzaj surowca należący do zbioru ("coal", "iron", "gold", "diamond", "emerald")
* `depth` - głębokość, z której wydobyto surowiec, zakres od 1 do 35
* `number` - rozmiar, zakres od 1 do 9
* `ets` - czas wydobycia surowca
* `its` - czas rejestracji zdarzenia w systemie.

# Zadania
Opracuj rozwiązania poniższych zadań. 
* Opieraj się strumieniu zdarzeń zgodnych ze schematem `MinecraftEvent`
* W każdym rozwiązaniu możesz skorzystać z jednego lub kilku poleceń EPL.
* Ostatnie polecenie będące ostatecznym rozwiązaniem zadania musi 
  * być poleceniem `select` 
  * posiadającym etykietę `answer`, przykładowo:
  ```aidl
    @name('answer') SELECT ore, depth, number, ets, its
    from MinecraftEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 3 sec)
  ```

## Zadanie 1
Utrzymuj informację o sumie wydobytego surowca dla każdego jego typu w ciągu ostatniej minuty.

Wyniki powinny zawierać, następujące kolumny:
- `ore` - typ surowca
- `number_sum` - suma wydobytego surowca zarejestrowana w ciągu ostatniej minuty.

## Zadanie 2
Wykrywaj przypadki, w których napotkano diamenty o liczności większej niż 6 na głębokości większej niż 12.

Wyniki powinny zawierać, następujące kolumny:
- `depth` - głębokość, na jakiej znaleziono diamenty
- `number` - rozmiar wydobytego surowca
- `ets` - czas wydobycia surowca
- `its` - czas rejestracji.

## Zadanie 3
Znajdź przypadki, w których wydobyto surowiec o liczności minimum 1.5 razy większej niż średni rozmiar wydobycia tego samego rodzaju surowca w ciągu ostatniej minuty.

Wyniki powinny zawierać, następujące kolumny:
- `ore` - rodzaj wydobytego surowca
- `depth` - głębokość
- `number` - rozmiar 
- `ets` - czas natrafienia 
- `its` - czas rejestracji.

## Zadanie 4
Dla dwóch poziomów głębokości 
- powyżej 20
- poniżej 10
utrzymywane są statystyki dotyczące wydobycia zarejestrowanego w ciągu ostatniej minuty dla każdego z typów surowca.

Utrzymuj porównanie tych samych surowców dla obu poziomów głębokości. 

Wyniki powinny zawierać, następujące kolumny:
- `ore` - rodzaj surowca
- `sumNumberHeaven` - suma surowca wydobytego na małej głębokości 
- `sumNumberHell` - suma surowca wydobytego na dużej głębokości.

## Zadanie 5
Znajduj serie wydobycia dowolnego rodzaju surowca zakończoną wydobyciem diamentów o liczności większej niż 5. Maksymalny czas serii nie może przekroczyć 30 sekund.

Zadbaj o to, aby znalezione serie się nie pokrywały.

Wyniki powinny zawierać, następujące kolumny:
- `ore` - rodzaj surowca zdarzenia początkowego
- `depth` - głębokość wydobycia dla zdarzenia początkowego
- `number` - liczność surowca zdarzenia początkowego
- `start_ets` - czas wydobycia zdarzenia początkowego
- `end_ets` - czas wydobycia diamentów kończącego serię

## Zadanie 6
Znajduj trójki zdarzeń wydobycia tego samego surowca, każdorazowo o wielkości powyżej 5, przy czym każde kolejne wydobycie będzie większe od poprzedniego. Odrzuć takie przypadki, w trakcie których wydobyto surowiec innego rodzaju.

Wyniki powinny zawierać następujące kolumny:
- `ore` - rodzaj surowca w wykrytej serii
- `number1` - liczność wydobycia w pierwszym zdarzeniu
- `number2` - liczność wydobycia w drugim zdarzeniu
- `number3` - liczność wydobycia w trzecim zdarzeniu

## Zadanie 7
Trzy wydobycia trzech różnych surowców są określane przez graczy Big3. 
Znajduj przypadki wystąpienia dwóch następujących po sobie Big3. 

Wyniki powinny zawierać, następujące kolumny:
- `start_its` - moment rejestracji pierwszego zdarzenia z pierwszego Big3
- `end_its` - moment rejestracji ostatniego zdarzenia z drugiego Big3.
- `sum_number` - sumę wydobytego surowca ze wszystkich sześciu zdarzeń.
