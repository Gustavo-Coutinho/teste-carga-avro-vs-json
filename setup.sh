#!/bin/bash

# Script de Setup do Projeto Kafka Carga Teste
# Cria a estrutura de diretórios e move os arquivos para locais corretos

set -e

echo "=========================================="
echo "Setup do Projeto Kafka Carga Teste"
echo "=========================================="
echo ""

# Cores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Criar estrutura de diretórios
echo -e "${BLUE}[1/5] Criando estrutura de diretórios...${NC}"
mkdir -p src/main/java/br/com/sandbox/kafka/aplicacoes
mkdir -p src/main/java/br/com/sandbox/kafka/util
mkdir -p src/main/resources/avro
echo -e "${GREEN}✓ Estrutura criada${NC}"
echo ""

# Mover arquivos Java - Classe Principal
echo -e "${BLUE}[2/5] Organizando classe principal...${NC}"
if [ -f "AplicacaoPrincipal.java" ]; then
    mv AplicacaoPrincipal.java src/main/java/br/com/sandbox/kafka/
    echo -e "${GREEN}✓ AplicacaoPrincipal.java movido${NC}"
fi

# Mover arquivos Java - Aplicações
echo -e "${BLUE}[3/5] Organizando aplicações...${NC}"
for file in ProdutorAvro.java ConsumidorAvro.java ProdutorJson.java ConsumidorJson.java; do
    if [ -f "$file" ]; then
        mv "$file" src/main/java/br/com/sandbox/kafka/aplicacoes/
        echo -e "${GREEN}✓ $file movido${NC}"
    fi
done

# Mover arquivos Java - Utilitários
echo -e "${BLUE}[4/5] Organizando utilitários...${NC}"
for file in ConfiguracaoKafka.java GeradorMensagemJson.java MetricasDesempenho.java; do
    if [ -f "$file" ]; then
        mv "$file" src/main/java/br/com/sandbox/kafka/util/
        echo -e "${GREEN}✓ $file movido${NC}"
    fi
done

# Mover schema Avro
echo -e "${BLUE}[5/5] Organizando schema Avro...${NC}"
if [ -f "avro_schema.avsc" ]; then
    mv avro_schema.avsc src/main/resources/avro/MensagemCarga.avsc
    echo -e "${GREEN}✓ MensagemCarga.avsc movido${NC}"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}Setup concluído com sucesso!${NC}"
echo "=========================================="
echo ""

# Verificar estrutura
echo "Estrutura final:"
tree -L 5 src/ 2>/dev/null || find src/ -type f

echo ""
echo "Próximos passos:"
echo "1. cp .env.template .env"
echo "2. Edite o arquivo .env com suas credenciais"
echo "3. mvn clean package"
echo "4. docker-compose build"
echo "5. Configure TIPO_APLICACAO no .env"
echo "6. docker-compose up"
