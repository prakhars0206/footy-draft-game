# Scheme deep-dive — flat +2 / +1 (FIFA 16), capped at 95  (COMMITTED)

Offset: **FIFA ≤15 → +2**, **FIFA 16 → +1**, **FIFA 17+ → 0**, then the result is **clamped at 95**.
Tier = club `optimalStrength`: Iconic ≥87 · Elite 83–86 · Pedigree 78–82 · Steady 72–77 · Minnow ≤71.

## A. Tier distribution per edition (this scheme)

| Edition | n | Iconic | Elite | Pedigree | Steady | Minnow | mean | maxClub | maxOVR |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| 2006/07 | 98 | 4.1% | 10.2% | 45.9% | 38.8% | 1.0% | 78.8 | 88 | 95 |
| 2007/08 | 98 | 4.1% | 10.2% | 37.8% | 43.9% | 4.1% | 78.3 | 88 | 93 |
| 2008/09 | 98 | 0.0% | 13.3% | 32.7% | 48.0% | 6.1% | 77.5 | 86 | 93 |
| 2009/10 | 98 | 2.0% | 8.2% | 35.7% | 51.0% | 3.1% | 77.6 | 87 | 92 |
| 2010/11 | 98 | 2.0% | 11.2% | 38.8% | 46.9% | 1.0% | 78.4 | 87 | 92 |
| 2011/12 | 98 | 3.1% | 12.2% | 38.8% | 45.9% | 0.0% | 78.6 | 89 | 95 |
| 2012/13 | 98 | 3.1% | 14.3% | 36.7% | 43.9% | 2.0% | 78.5 | 88 | 95 |
| 2013/14 | 98 | 3.1% | 11.2% | 32.7% | 53.1% | 0.0% | 78.2 | 88 | 95 |
| 2014/15 | 98 | 3.1% | 10.2% | 34.7% | 46.9% | 5.1% | 77.8 | 89 | 95 |
| 2015/16 | 98 | 3.1% | 8.2% | 36.7% | 49.0% | 3.1% | 78.1 | 88 | 95 |
| 2016/17 | 98 | 3.1% | 9.2% | 37.8% | 49.0% | 1.0% | 78.1 | 88 | 94 |
| 2017/18 | 98 | 2.0% | 10.2% | 40.8% | 45.9% | 1.0% | 78.1 | 88 | 94 |
| 2018/19 | 98 | 4.1% | 10.2% | 36.7% | 48.0% | 1.0% | 78.1 | 88 | 94 |
| 2019/20 | 98 | 3.1% | 11.2% | 38.8% | 44.9% | 2.0% | 78.1 | 88 | 94 |
| 2020/21 | 96 | 3.1% | 10.4% | 32.3% | 51.0% | 3.1% | 77.8 | 87 | 93 |
| 2021/22 | 98 | 2.0% | 11.2% | 34.7% | 49.0% | 3.1% | 77.9 | 87 | 93 |
| 2022/23 | 98 | 1.0% | 13.3% | 32.7% | 51.0% | 2.0% | 77.9 | 87 | 91 |
| 2023/24 | 96 | 1.0% | 13.5% | 35.4% | 46.9% | 3.1% | 77.7 | 87 | 91 |
| 2024/25 | 96 | 1.0% | 10.4% | 41.7% | 45.8% | 1.0% | 77.9 | 87 | 91 |
| 2025/26 | 96 | 3.1% | 7.3% | 38.5% | 47.9% | 3.1% | 78.0 | 87 | 91 |
| **OLD 07–15** | 882 | **2.7%** | **11.2%** | **37.1%** | **46.5%** | **2.5%** | — | — | — |
| **MODERN 16–26** | 1070 | **2.4%** | **10.5%** | **36.9%** | **48.0%** | **2.1%** | — | — | — |

## B. What the 94-cap does to the top end

`capped` = players whose offset rating would exceed 94 (so they're pulled down to 94). `raw→94` = how many *distinct* raw ratings collapse onto 94 (top-end compression). `#at94` = players sitting at 94 after the scheme — compare old vs modern for over-density.

| Edition | offset | rawMax | capped | raw→94 | #at 94 |
|---|--:|--:|--:|--:|--:|
| 2006/07 | +2 | 93 | 0 | 1 | 1 |
| 2007/08 | +2 | 91 | 0 | 0 | 0 |
| 2008/09 | +2 | 91 | 0 | 0 | 0 |
| 2009/10 | +2 | 90 | 0 | 0 | 0 |
| 2010/11 | +2 | 90 | 0 | 0 | 0 |
| 2011/12 | +2 | 94 | 1 | 1 | 1 |
| 2012/13 | +2 | 94 | 1 | 1 | 1 |
| 2013/14 | +2 | 94 | 1 | 1 | 1 |
| 2014/15 | +2 | 93 | 0 | 1 | 1 |
| 2015/16 | +1 | 94 | 0 | 1 | 1 |
| 2016/17 | +0 | 94 | 0 | 0 | 0 |
| 2017/18 | +0 | 94 | 0 | 0 | 0 |
| 2018/19 | +0 | 94 | 0 | 0 | 0 |
| 2019/20 | +0 | 94 | 0 | 0 | 0 |
| 2020/21 | +0 | 93 | 0 | 0 | 0 |
| 2021/22 | +0 | 93 | 0 | 0 | 0 |
| 2022/23 | +0 | 91 | 0 | 0 | 0 |
| 2023/24 | +0 | 91 | 0 | 0 | 0 |
| 2024/25 | +0 | 91 | 0 | 0 | 0 |
| 2025/26 | +0 | 91 | 0 | 0 | 0 |

## C. Every player rated ≥91 (raw), per edition

`raw → final` after +offset and the 94 cap. **CAPPED** marks players the cap actually pulled down.

### 2006/07  (offset +2)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| W. Rooney | Manchester United | 93 | 95 |  |
| G. Coupet | Olympique Lyonnais | 92 | 94 |  |
| T. Henry | Arsenal | 91 | 93 |  |
| J. Terry | Chelsea | 91 | 93 |  |
| A. Nesta | AC Milan | 91 | 93 |  |
| Ronaldinho | FC Barcelona | 91 | 93 |  |
| F. Cannavaro | Real Madrid | 91 | 93 |  |

### 2007/08  (offset +2)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| Cristiano Ronaldo | Manchester United | 91 | 93 |  |
| G. Buffon | Juventus | 91 | 93 |  |
| A. Nesta | AC Milan | 91 | 93 |  |
| T. Henry | FC Barcelona | 91 | 93 |  |
| Ronaldinho | FC Barcelona | 91 | 93 |  |

### 2008/09  (offset +2)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| Casillas | Real Madrid | 91 | 93 |  |

### 2011/12  (offset +2)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | FC Barcelona | 94 | 95 | CAPPED |
| Cristiano Ronaldo | Real Madrid | 92 | 94 |  |
| Iniesta | FC Barcelona | 91 | 93 |  |
| Xavi | FC Barcelona | 91 | 93 |  |

### 2012/13  (offset +2)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | FC Barcelona | 94 | 95 | CAPPED |
| Cristiano Ronaldo | Real Madrid | 92 | 94 |  |

### 2013/14  (offset +2)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | FC Barcelona | 94 | 95 | CAPPED |
| Cristiano Ronaldo | Real Madrid | 92 | 94 |  |

### 2014/15  (offset +2)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | FC Barcelona | 93 | 95 |  |
| Cristiano Ronaldo | Real Madrid CF | 92 | 94 |  |

### 2015/16  (offset +1)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | FC Barcelona | 94 | 95 |  |
| Cristiano Ronaldo | Real Madrid CF | 93 | 94 |  |

### 2016/17  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| Cristiano Ronaldo | Real Madrid CF | 94 | 94 |  |
| L. Messi | FC Barcelona | 93 | 93 |  |
| L. Suárez | FC Barcelona | 92 | 92 |  |
| Neymar | FC Barcelona | 92 | 92 |  |
| M. Neuer | FC Bayern München | 92 | 92 |  |

### 2017/18  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| Cristiano Ronaldo | Real Madrid CF | 94 | 94 |  |
| L. Messi | FC Barcelona | 93 | 93 |  |
| L. Suárez | FC Barcelona | 92 | 92 |  |
| M. Neuer | FC Bayern München | 92 | 92 |  |
| Neymar | Paris Saint-Germain | 92 | 92 |  |
| R. Lewandowski | FC Bayern München | 91 | 91 |  |

### 2018/19  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| Cristiano Ronaldo | Juventus | 94 | 94 |  |
| L. Messi | FC Barcelona | 94 | 94 |  |
| Neymar | Paris Saint-Germain | 92 | 92 |  |
| L. Suárez | FC Barcelona | 91 | 91 |  |
| Sergio Ramos | Real Madrid CF | 91 | 91 |  |
| L. Modrić | Real Madrid CF | 91 | 91 |  |
| E. Hazard | Chelsea | 91 | 91 |  |
| K. De Bruyne | Manchester City | 91 | 91 |  |
| De Gea | Manchester United | 91 | 91 |  |

### 2019/20  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | FC Barcelona | 94 | 94 |  |
| Cristiano Ronaldo | Juventus | 93 | 93 |  |
| Neymar Jr | Paris Saint-Germain | 92 | 92 |  |
| E. Hazard | Real Madrid CF | 91 | 91 |  |
| K. De Bruyne | Manchester City | 91 | 91 |  |
| J. Oblak | Atlético de Madrid | 91 | 91 |  |

### 2020/21  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | FC Barcelona | 93 | 93 |  |
| Cristiano Ronaldo | Juventus | 92 | 92 |  |
| R. Lewandowski | FC Bayern München | 91 | 91 |  |
| Neymar Jr | Paris Saint-Germain | 91 | 91 |  |
| K. De Bruyne | Manchester City | 91 | 91 |  |
| J. Oblak | Atlético de Madrid | 91 | 91 |  |

### 2021/22  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | Paris Saint-Germain | 93 | 93 |  |
| R. Lewandowski | FC Bayern München | 92 | 92 |  |
| Neymar Jr | Paris Saint-Germain | 91 | 91 |  |
| K. Mbappé | Paris Saint-Germain | 91 | 91 |  |
| Cristiano Ronaldo | Manchester United | 91 | 91 |  |
| K. De Bruyne | Manchester City | 91 | 91 |  |
| J. Oblak | Atlético de Madrid | 91 | 91 |  |

### 2022/23  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| L. Messi | Paris Saint Germain | 91 | 91 |  |
| K. Mbappé | Paris Saint Germain | 91 | 91 |  |
| K. Benzema | Real Madrid | 91 | 91 |  |
| R. Lewandowski | FC Barcelona | 91 | 91 |  |
| K. De Bruyne | Manchester City | 91 | 91 |  |

### 2023/24  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| K. Mbappé | Paris Saint Germain | 91 | 91 |  |
| E. Haaland | Manchester City | 91 | 91 |  |
| K. De Bruyne | Manchester City | 91 | 91 |  |

### 2024/25  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| Rodri | Manchester City | 91 | 91 |  |
| Mohamed Salah Hamed Ghaly | Liverpool | 91 | 91 |  |

### 2025/26  (offset +0)

| Player | Club | raw | → final | |
|---|---|--:|--:|---|
| Mohamed Salah | Liverpool | 91 | 91 |  |
| Kylian Mbappé | Real Madrid | 91 | 91 |  |

