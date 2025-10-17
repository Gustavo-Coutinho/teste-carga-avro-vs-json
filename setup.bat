@echo off
REM Script de Setup do Projeto Kafka Carga Teste (Windows)
REM Cria a estrutura de diretórios e move os arquivos para locais corretos

echo ==========================================
echo Setup do Projeto Kafka Carga Teste
echo ==========================================
echo.

REM Criar estrutura de diretórios
echo [1/5] Criando estrutura de diretorios...
mkdir src\main\java\br\com\sandbox\kafka\aplicacoes 2>nul
mkdir src\main\java\br\com\sandbox\kafka\util 2>nul
mkdir src\main\resources\avro 2>nul
echo OK - Estrutura criada
echo.

REM Mover classe principal
echo [2/5] Organizando classe principal...
if exist AplicacaoPrincipal.java (
    move AplicacaoPrincipal.java src\main\java\br\com\sandbox\kafka\
    echo OK - AplicacaoPrincipal.java movido
)

REM Mover aplicações
echo [3/5] Organizando aplicacoes...
if exist ProdutorAvro.java move ProdutorAvro.java src\main\java\br\com\sandbox\kafka\aplicacoes\
if exist ConsumidorAvro.java move ConsumidorAvro.java src\main\java\br\com\sandbox\kafka\aplicacoes\
if exist ProdutorJson.java move ProdutorJson.java src\main\java\br\com\sandbox\kafka\aplicacoes\
if exist ConsumidorJson.java move ConsumidorJson.java src\main\java\br\com\sandbox\kafka\aplicacoes\
echo OK - Aplicacoes movidas

REM Mover utilitários
echo [4/5] Organizando utilitarios...
if exist ConfiguracaoKafka.java move ConfiguracaoKafka.java src\main\java\br\com\sandbox\kafka\util\
if exist GeradorMensagemJson.java move GeradorMensagemJson.java src\main\java\br\com\sandbox\kafka\util\
if exist MetricasDesempenho.java move MetricasDesempenho.java src\main\java\br\com\sandbox\kafka\util\
echo OK - Utilitarios movidos

REM Mover schema Avro
echo [5/5] Organizando schema Avro...
if exist avro_schema.avsc (
    move avro_schema.avsc src\main\resources\avro\MensagemCarga.avsc
    echo OK - MensagemCarga.avsc movido
)

echo.
echo ==========================================
echo Setup concluido com sucesso!
echo ==========================================
echo.

echo Proximos passos:
echo 1. copy .env.template .env
echo 2. Edite o arquivo .env com suas credenciais
echo 3. mvn clean package
echo 4. docker-compose build
echo 5. Configure TIPO_APLICACAO no .env
echo 6. docker-compose up

pause
