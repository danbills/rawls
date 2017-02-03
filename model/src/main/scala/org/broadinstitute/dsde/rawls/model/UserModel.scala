package org.broadinstitute.dsde.rawls.model

import spray.json._

sealed trait UserAuthRef
case class RawlsUserRef(userSubjectId: RawlsUserSubjectId) extends UserAuthRef
case class RawlsGroupRef(groupName: RawlsGroupName) extends UserAuthRef
case class RawlsRealmRef(groupName: RawlsGroupName) extends UserAuthRef

object RawlsGroupRef {
  implicit def toRealmRef(ref: RawlsGroupRef): RawlsRealmRef = RawlsRealmRef(ref.groupName)
}

object RawlsRealmRef {
  implicit def toGroupRef(ref: RawlsRealmRef): RawlsGroupRef = RawlsGroupRef(ref.groupName)
}

object RawlsGroupName {
  implicit def toRealmName(name: RawlsGroupName): RawlsRealmName = RawlsRealmName(name.value)
}

object RawlsRealmName {
  implicit def toGroupName(name: RawlsRealmName): RawlsGroupName = RawlsGroupName(name.value)
}

sealed trait UserAuthType { val value: String }
case class RawlsUserEmail(value: String) extends UserAuthType
case class RawlsUserSubjectId(value: String) extends UserAuthType
case class RawlsGroupName(value: String) extends UserAuthType
case class RawlsGroupEmail(value: String) extends UserAuthType
case class RawlsRealmName(value: String) extends UserAuthType
case class RawlsBillingAccountName(value: String) extends UserAuthType
case class RawlsBillingProjectName(value: String) extends UserAuthType

object UserModelJsonSupport extends JsonSupport {

  case class UserModelJsonFormatter[T <: UserAuthType](create: String => T) extends RootJsonFormat[T] {
    def read(obj: JsValue): T = obj match {
      case JsString(value) => create(value)
      case _ => throw new DeserializationException("could not deserialize user object")
    }

    def write(obj: T): JsValue = JsString(obj.value)
  }

  implicit val RawlsUserEmailFormat = UserModelJsonFormatter(RawlsUserEmail)
  implicit val RawlsUserSubjectIdFormat = UserModelJsonFormatter(RawlsUserSubjectId)

  implicit val RawlsGroupNameFormat = UserModelJsonFormatter(RawlsGroupName.apply)
  implicit val RawlsGroupEmailFormat = UserModelJsonFormatter(RawlsGroupEmail)
  implicit val RawlsRealmNameFormat = UserModelJsonFormatter(RawlsRealmName.apply)
  implicit val RawlsBillingAccountNameFormat = UserModelJsonFormatter(RawlsBillingAccountName)
  implicit val RawlsBillingProjectNameFormat = UserModelJsonFormatter(RawlsBillingProjectName)

  implicit val RawlsUserRefFormat = jsonFormat1(RawlsUserRef)
  implicit val RawlsGroupRefFormat = jsonFormat1(RawlsGroupRef.apply)
  implicit val RawlsRealmRefFormat = jsonFormat1(RawlsRealmRef.apply)
}