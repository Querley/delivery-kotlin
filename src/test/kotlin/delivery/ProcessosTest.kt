package delivery

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ProcessosTest {
    @get:Rule val temporaria = TemporaryFolder()
    private val pasta get() = temporaria.root.toPath()

    private fun iniciar(app: String, entrada: String): Process {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val processo = ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-cp", System.getProperty("delivery.classpath"),
            "delivery.$app.${if (app == "cliente") "Cliente" else "Restaurante"}Kt", "--dados", pasta.toString())
            .redirectErrorStream(true).start()
        processo.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(entrada) }
        return processo
    }

    private fun resultado(processo: Process): String {
        if (!processo.waitFor(30, TimeUnit.SECONDS)) {
            processo.destroyForcibly()
            fail("O aplicativo não encerrou em 30 segundos.")
        }
        val saida = processo.inputStream.bufferedReader(Charsets.UTF_8).readText()
        assertEquals(saida, 0, processo.exitValue())
        return saida
    }

    @Test fun aplicativosIndependentesCompartilhamPedidoAteEntrega() {
        val cadastro = resultado(iniciar("restaurante", "2\nPizzaria\npizza@exemplo.com\nRua A\n1\nPizza\n45\n2\nRefri\n10\n\n0\n0\n"))
        assertTrue(cadastro.contains("Restaurante cadastrado"))
        val compra = resultado(iniciar("cliente", "2\nAna\n62999998888\nRua B\n1\n1\n1\n1\n2\n2\n\nS\n0\n0\n"))
        assertTrue(compra.contains("Total: R$ 65,00"))
        assertTrue(compra.contains("Pedido confirmado"))
        val id = Delivery(pasta).pedidosCliente("62999998888", false).first().idPedido
        val atualizacao = resultado(iniciar("restaurante", "1\npizza@exemplo.com\n2\n0\n3\n$id\n4\n0\n0\n"))
        assertTrue(atualizacao.contains("Status: 0 - SOLICITADO"))
        assertTrue(atualizacao.contains("Status atualizado"))
        val consulta = resultado(iniciar("cliente", "1\n62999998888\n2\n3\n0\n0\n"))
        assertTrue(consulta.contains("Nenhum pedido encontrado"))
        assertTrue(consulta.contains("Status: 4 - ENTREGUE"))
        assertTrue(consulta.contains("Total: R$ 65,00"))
    }

    @Test fun doisClientesSimultaneosNaoPerdemCadastrosOuPedidos() {
        Delivery(pasta).cadastrarRestaurante("Pizza", "pizza@exemplo.com", "Rua A",
            listOf(ItemCardapio(1, "Pizza", "45.00".toBigDecimal())))
        val primeiro = iniciar("cliente", "2\nAna\n62999998888\nRua B\n1\n1\n1\n1\n\nS\n0\n0\n")
        val segundo = iniciar("cliente", "2\nBruno\n62988887777\nRua C\n1\n1\n1\n2\n\nS\n0\n0\n")
        assertTrue(resultado(primeiro).contains("Pedido confirmado"))
        assertTrue(resultado(segundo).contains("Pedido confirmado"))
        val sistema = Delivery(pasta)
        assertNotNull(sistema.entrarCliente("62999998888"))
        assertNotNull(sistema.entrarCliente("62988887777"))
        assertEquals(2, sistema.pedidosRestaurante("pizza@exemplo.com").map { it.idPedido }.distinct().size)
    }
}
