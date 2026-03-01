# pokus1 — SerializationUtil

Java utilita pro serializaci objektů s volitelnou kompresí. Podporuje šest kompresních kodeků včetně automatického výběru nejlepšího formátu.

## Požadavky

- Java 17+
- Gradle 9.x (přiložen wrapper)

## Sestavení a testy

```bash
./gradlew build
./gradlew test
```

## Použití

### Serializace bez komprese

```java
SerializationUtil.serialize(myObject, Path.of("data.ser"));
```

### Serializace s automatickým výběrem komprese

Zkusí všechny dostupné kodeky (včetně kombinací s Huffmanem) a zapíše nejmenší výsledek:

```java
SerializationUtil.serialize(myObject, Path.of("data.ser"), true);
```

### Serializace s explicitním kodekem

```java
SerializationUtil.serialize(myObject, Path.of("data.ser.gz"), SerializationUtil.Compression.GZIP);
SerializationUtil.serialize(myObject, Path.of("data.ser.xz"), SerializationUtil.Compression.XZ);
SerializationUtil.serialize(myObject, Path.of("data.ser.bz2"), SerializationUtil.Compression.BZIP2);
SerializationUtil.serialize(myObject, Path.of("data.ser"), SerializationUtil.Compression.HUFFMAN);
SerializationUtil.serialize(myObject, Path.of("data.ser"), SerializationUtil.Compression.RLE);
```

### Deserializace

Formát se detekuje automaticky z prvního bajtu souboru — není třeba nic nastavovat:

```java
MyType obj = SerializationUtil.deserialize(Path.of("data.ser"));
```

> **Bezpečnostní upozornění:** Java nativní deserializace je známý vektor vzdáleného spuštění kódu. Deserializujte pouze data z důvěryhodných zdrojů.

## Podporované kodeky

| Enum hodnota | Popis                                           | Magic byte |
|--------------|-------------------------------------------------|------------|
| `NONE`       | Žádná komprese                                  | `0x00`     |
| `GZIP`       | GZIP                                            | `0x01`     |
| `XZ`         | XZ / LZMA2                                     | `0x02`     |
| `BZIP2`      | BZip2                                           | `0x03`     |
| `HUFFMAN`    | Huffman-only (raw DEFLATE)                      | `0x04`     |
| `RLE`        | Run-Length Encoding                             | `0x05`     |

Automatický režim (`compress = true`) navíc zkouší kombinace `GZIP+Huffman` (`0x11`), `XZ+Huffman` (`0x12`), `BZIP2+Huffman` (`0x13`) a `RLE+Huffman` (`0x15`).

## Architektura

```
SerializationUtil          — veřejné API (serialize / deserialize)
├── RleOutputStream        — package-private streaming RLE enkodér
└── RleInputStream         — package-private streaming RLE dekodér
```

Každý soubor začíná jedním magic bajtem identifikujícím použitý formát. `deserialize()` z něj automaticky sestaví správný zásobník dekompresorů.

Při automatickém výběru (`compress = true`) se objekt nejprve serializuje do paměti, pak se každý formát otestuje přes `CountingOutputStream` (žádná data se neukládají, počítají se jen bajty) a výsledný soubor se zapíše jediným průchodem přes vítězný formát.

## Závislosti

| Knihovna                          | Verze  | Použití          |
|-----------------------------------|--------|------------------|
| `org.tukaani:xz`                  | 1.9    | XZ / LZMA2       |
| `org.apache.commons:commons-compress` | 1.26.2 | BZip2        |
| `org.junit.jupiter:junit-jupiter` | 5.10.0 | testy            |

## CI / automatické review

Každý pull request prochází třemi automatizovanými kroky:

| Workflow                  | Popis                                                        |
|---------------------------|--------------------------------------------------------------|
| `ci.yml`                  | Build a testy (Gradle)                                       |
| `claude-review.yml`       | Claude přidá inline komentáře a souhrnné review do PR        |
| `copilot-review.yml`      | GitHub Copilot je automaticky přidán jako reviewer PR        |

Reakce na issues jsou zajištěny workflowem `claude-issue-agent.yml`.
