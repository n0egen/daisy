import daisy.lang._
import Real._
import daisy.lang.Vector._

object heat1d {

	def heat1d(ax: Vector): Real = {
require(ax >= 1.0 && ax <= 2.0 && ax.size(513)
	 && ax.specV(Set(((3, 8),(1.19, 1.86)), ((9, 13),(1.57, 1.63)), ((35, 39),(1.19, 1.91)),
((40, 45),(1.63, 1.96)), ((46, 46),(1.01, 1.96)), ((50, 60),(1.43, 1.87)),
((61, 62),(1.55, 1.79)), ((63, 64),(1.47, 1.84)), ((65, 71),(1.11, 1.53)),
((72, 82),(1.44, 1.82)), ((83, 83),(1.12, 1.54)), ((84, 85),(1.18, 1.95)),
((86, 86),(1.4, 1.88)), ((87, 94),(1.05, 1.68)), ((96, 100),(1.04, 1.34)),
((101, 101),(1.23, 1.61)), ((102, 103),(1.98, 1.99)), ((105, 106),(1.49, 1.87)),
((107, 117),(1.4, 1.92)), ((118, 119),(1.1, 1.89)), ((131, 134),(1.61, 1.76)),
((135, 135),(1.17, 1.39)), ((136, 136),(1.57, 1.6)), ((137, 137),(1.02, 1.52)),
((138, 138),(1.43, 1.86)), ((139, 149),(1.59, 1.93)), ((151, 161),(1.23, 1.5)),
((239, 249),(1.18, 1.51)), ((294, 304),(1.88, 2.0)), ((307, 308),(1.32, 1.48)),
((309, 311),(1.4, 1.85)), ((312, 312),(1.27, 1.93)), ((313, 323),(1.09, 1.31)),
((324, 324),(1.58, 1.9)), ((325, 332),(1.1, 1.35)), ((334, 336),(1.75, 1.83)),
((340, 342),(1.36, 1.79)), ((344, 347),(1.58, 1.95)), ((348, 348),(1.29, 1.46)),
((349, 349),(1.73, 2.0)), ((350, 360),(1.58, 1.72)), ((361, 362),(1.76, 1.96)),
((363, 363),(1.45, 1.93)), ((364, 364),(1.38, 1.86)), ((365, 366),(1.35, 1.44)),
((367, 371),(1.02, 1.12)), ((372, 382),(1.09, 1.96)), ((446, 456),(1.2, 1.32)),
((479, 483),(1.09, 1.5)), ((485, 485),(1.4, 1.71)), ((486, 491),(1.82, 2.0))))
	)

          if (ax.length() <= 1) {
            ax.head
        } else {
            val coef = Vector(List(0.25, 0.5, 0.25))
            val updCoefs: Vector = ax.slideReduce(3,1)(v =>  (coef*v).sum())
            heat1d(updCoefs)
        }
    }


}