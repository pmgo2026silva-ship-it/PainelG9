# Painel G9 — launcher para a central Android do Civic G9

Launcher próprio (sem dependências, APK pequeno) para a central CSK/UP Allwinner, Android 12, 2 GB de RAM.
Não troca o sistema nem o firmware: se algo não agradar, é só voltar ao launcher original.

## O que tem
- Tela inicial: relógio, clima (Open-Meteo, precisa de internet), velocímetro, silhueta ou foto do seu carro, consumo, 5 atalhos de apps, controle de música e volume.
- Gaveta de apps (toque longo num app = fixar em um atalho).
- Drive Lab (OBD2 ELM327 por Bluetooth ou Wi-Fi): giro, temperatura, bateria, carga, acelerador, consumo instantâneo/médio, gasto em reais, leitura de códigos de falha em português. **Somente leitura.**
- 4 temas, foto de fundo, foto do carro (PNG transparente), aviso sonoro de velocidade, boas-vindas com o nome do carro.

## Gerar o APK (sem instalar nada no PC)
1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta.
2. Aba **Actions** → "Compilar APK" → **Run workflow**.
3. Quando terminar, baixe o artefato **PainelG9-apk** (contém `app-debug.apk`).

Pelo Android Studio: abra a pasta, **Build > Build APK(s)**.

## Instalar na central
1. Copie o APK para um pendrive e abra pelo gerenciador de arquivos da central (autorize instalar apps de fontes desconhecidas).
2. Abra o app, conceda Localização e Bluetooth.
3. Ajustes → **Launcher padrão** → Painel G9 → Sempre.
4. Ajustes → **Mostrar a música** → libere "Painel G9" (para ver título/artista).
5. OBD2: pareie o ELM327 em Configurações > Bluetooth (senha 1234 ou 0000), depois Ajustes → Escolher adaptador → Conectar.
   Adaptadores Bluetooth clássico e Wi-Fi funcionam. Modelos só BLE não funcionam nesta versão.

## Se a central não gostar de launcher de terceiros
Alguns firmwares forçam o launcher de fábrica. Nesse caso o app continua acessível como app comum
(ícone "Painel G9") e você usa como tela principal sem defini-lo como padrão.
