package app

import cats.data.Kleisli
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import dev.usommerl.BuildInfo
import eu.timepit.refined.auto._
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.implicits._
import org.http4s.server.middleware.CORS
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.apispec.Tag
import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.{OpenAPI, Server}
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import cats.data.NonEmptyList

object Api {
  def apply[F[_]: Concurrent: ContextShift: Timer](config: ApiDocsConfig, client: CounteeClient[F]): Kleisli[F, Request[F], Response[F]] = {

    val dsl = Http4sDsl[F]
    import dsl._

    val apis: List[TapirApi[F]] = List(MetricsApi(client))

    val docs: OpenAPI = OpenAPIDocsInterpreter
      .toOpenAPI(apis.flatMap(_.endpoints), openapi.Info(BuildInfo.name, BuildInfo.version, config.description))
      .servers(List(Server(config.serverUrl)))
      .tags(apis.map(_.tag))

    val redirectRootToDocs = HttpRoutes.of[F] { case path @ GET -> Root => PermanentRedirect(Location(path.uri / "docs")) }

    val routes: List[HttpRoutes[F]] = apis.map(_.routes) ++ List(new SwaggerHttp4s(docs.toYaml).routes, redirectRootToDocs)

    CORS(routes.reduce(_ <+> _)).orNotFound
  }
}

object MetricsApi {
  def apply[F[_]: Concurrent: ContextShift: Timer](client: CounteeClient[F]) = new TapirApi[F] {
    override val tag                  = Tag("Metrics", None)
    override lazy val serverEndpoints = List(metrics)

    private val metrics: ServerEndpoint[Unit, StatusCode, String, Any, F] =
      endpoint.get
        .summary("Countee counter values as Prometheus metrics")
        .tag(tag.name)
        .in("metrics")
        .out(stringBody)
        .errorOut(statusCode)
        .serverLogic(_ => client.fetch.map(toExpositionFormat(_).asRight))

    /**
      * See: https://github.com/prometheus/docs/blob/master/content/docs/instrumenting/exposition_formats.md
      */
    private def toExpositionFormat(l: NonEmptyList[CounteeRecord]): String = {
      val metricName1 = "countee_first_counter_item_value"
      val metricName2 = s"${metricName1}_ts_provided"
      val docMetric1 =
        s"# HELP $metricName1 Value of the first counter item.\n# TYPE $metricName1 gauge\n"
      val docMetric2 =
        s"# HELP $metricName2 Value of the first counter item (Uses the provided timestamp from Countee)\n# TYPE $metricName2 gauge\n"

      val metrics1 = l.foldLeft(docMetric1) {
        case (acc, r) => s"""${acc}${metricName1}{name="${r.name}"}\t${r.firstCounterItemVal}\n"""
      }
      val metrics2 = l.foldLeft(docMetric2) {
        case (acc, r) => s"""${acc}${metricName2}{name="${r.name}"}\t${r.firstCounterItemVal}\t${r.tsSeconds * 1000}\n"""
      }
      (metrics1 + metrics2)
    }
  }
}

abstract class TapirApi[F[_]: Concurrent: ContextShift: Timer] {
  def tag: Tag
  def serverEndpoints: List[ServerEndpoint[_, _, _, Any, F]]
  def endpoints: List[Endpoint[_, _, _, _]] = serverEndpoints.map(_.endpoint)
  def routes: HttpRoutes[F]                 = Http4sServerInterpreter.toRoutes(serverEndpoints)
}
